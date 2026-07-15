package io.atworks.specscan.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
import io.atworks.specscan.analysis.application.RuleOutputService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.ResponseBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.EndpointExtractor;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.IngestionMetadata;
import io.atworks.specscan.ingestion.domain.JavaInventorySummary;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.SafetyPolicyHint;
import io.atworks.specscan.ingestion.domain.SourceRootCandidate;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAssemblyServiceTest {

    private OpenApiAssemblyService assemblyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        assemblyService = new OpenApiAssemblyService();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testOpenApiAssemblyAndYamlGeneration(@TempDir Path tempDir) throws IOException, IngestionException {
        Path outputPath = tempDir.resolve("openapi.yaml");
        Path srcRoot = tempDir.resolve("src/main/java");
        Path dtoDir = srcRoot.resolve("io/atworks/dto");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("VisitRequest.java"), """
            package io.atworks.dto;

            import java.time.LocalDate;
            import java.util.UUID;

            public record VisitRequest(
                String description,
                LocalDate visitDate,
                PetType petType,
                OwnerSnapshot owner,
                UUID requestId
            ) {
                public record OwnerSnapshot(String firstName, Address address) {}
                public record Address(String city) {}
                public enum PetType {
                    DOG, CAT
                }
            }
        """);
        Files.writeString(dtoDir.resolve("VisitResponse.java"), """
            package io.atworks.dto;

            import java.time.LocalDate;
            import java.util.List;

            public record VisitResponse(
                String name,
                LocalDate nextVisitDate,
                Specialty specialty,
                List<String> notes
            ) {
                public enum Specialty {
                    SURGERY, DENTISTRY
                }
            }
        """);

        SourceTrace dummyTrace = new SourceTrace("src/main/java/io/atworks/controller/VisitController.java", 10, 15);
        RequestBinding pathBinding = new RequestBinding("petId", BindingLocation.PATH, "Long", true, null, null, null, List.of(), dummyTrace);
        RequestBinding bodyBinding = new RequestBinding("visitForm", BindingLocation.BODY, "VisitRequest", true, null, null, null, List.of(), dummyTrace);
        ResponseBinding responseBinding = new ResponseBinding("List<VisitResponse>", dummyTrace);

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/owners/*/pets/{petId}/visits",
            "io.atworks.controller.VisitController",
            "createVisit",
            List.of(pathBinding, bodyBinding),
            responseBinding,
            dummyTrace
        );

        StaticScanResult scanResult = new StaticScanResult(
            List.of(endpoint),
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );

        ApiConditionDraft draft1 = new ApiConditionDraft("visitForm.description", "SIZE", "min=5, max=20", "@Size", dummyTrace);
        ApiConditionDraft draft2 = new ApiConditionDraft("visitForm.requestId", "NOT_NULL", "true", "@NotNull", dummyTrace);
        ApiConditionDraft draft3 = new ApiConditionDraft("visitForm.owner.address.city", "NOT_BLANK", "true", "@NotBlank", dummyTrace);
        ValidationCandidate candidate1 = new ValidationCandidate("cand-1", "SERVICE_HINT", "petId", "validated", 1.0, dummyTrace);
        ValidationCandidate candidate2 = new ValidationCandidate("cand-2", "SERVICE_HINT", "petId", "validated", 1.0, dummyTrace);
        ValidationCandidate candidate3 = new ValidationCandidate("cand-3", "SERVICE_HINT", "unknownField", "validated", 1.0, dummyTrace);

        ValidationExtractionResult extractResult = new ValidationExtractionResult(
            List.of(draft1, draft2, draft3),
            List.of(candidate1, candidate2, candidate3),
            List.of()
        );

        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);

        assemblyService.assemble(scanResult, extractResult, repositorySource, outputPath);

        assertThat(outputPath).exists();
        Path structuredOutputPath = tempDir.resolve("api-spec-analysis.json");
        assertThat(structuredOutputPath).exists();
        Path executionOutputPath = tempDir.resolve("api-execution-model.json");
        assertThat(executionOutputPath).exists();
        Path graphOutputPath = tempDir.resolve("validation-evidence-graph.json");
        assertThat(graphOutputPath).exists();

        String yamlContent = Files.readString(outputPath);
        JsonNode structuredJson = objectMapper.readTree(Files.readString(structuredOutputPath));
        JsonNode executionJson = objectMapper.readTree(Files.readString(executionOutputPath));
        JsonNode graphJson = objectMapper.readTree(Files.readString(graphOutputPath));

        assertThat(yamlContent)
            .contains("openapi: 3.0.3")
            .contains("/owners/*/pets/{petId}/visits:")
            .contains("requestBody:")
            .contains("description:")
            .contains("minLength: 5")
            .contains("petType:")
            .contains("enum:")
            .contains("responses:")
            .doesNotContain("#/components/schemas")
            .doesNotContain("__mvc_view__");

        assertThat(structuredJson.at("/apiVersions/0/method").asText()).isEqualTo("POST");
        assertThat(structuredJson.at("/apiVersions/0/endpoint").asText()).isEqualTo("/owners/*/pets/{petId}/visits");

        assertThat(executionJson.at("/operations/0/request/pathParams/0/name").asText()).isEqualTo("petId");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/description/minLength").asInt()).isEqualTo(5);
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/description/maxLength").asInt()).isEqualTo(20);
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/requestId/format").asText()).isEqualTo("uuid");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/requestId/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/visitDate/format").asText()).isEqualTo("date");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/petType/enumValues/0").asText()).isEqualTo("DOG");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/owner/properties/address/properties/city/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/0/request/bodyExample/visitDate").asText()).isEqualTo("2024-01-01");
        assertThat(executionJson.at("/operations/0/request/bodyExample/petType").asText()).isEqualTo("DOG");

        assertThat(executionJson.at("/operations/0/response200/schema/type").asText()).isEqualTo("array");
        assertThat(executionJson.at("/operations/0/response200/schema/items/properties/nextVisitDate/format").asText()).isEqualTo("date");
        assertThat(executionJson.at("/operations/0/response200/schema/items/properties/specialty/enumValues/1").asText()).isEqualTo("DENTISTRY");

        JsonNode requestPreconditions = executionJson.at("/operations/0/requestPreconditions");
        assertThat(requestPreconditions).hasSize(3);
        assertThat(requestPreconditions.toString()).contains("$.description");
        assertThat(requestPreconditions.toString()).contains("$.requestId");
        assertThat(requestPreconditions.toString()).contains("$.owner.address.city");
        assertThat(requestPreconditions.toString()).doesNotContain("$.visitForm");
        assertThat(requestPreconditions.toString()).doesNotContain("$.city\"");
        assertThat(requestPreconditions.toString()).doesNotContain("$.petId");
        assertThat(requestPreconditions.toString()).doesNotContain("unknownField");

        JsonNode responseAssertions = executionJson.at("/operations/0/responseAssertions");
        assertThat(responseAssertions).isEmpty();
        assertThat(graphJson.at("/nodes").isArray()).isTrue();
        assertThat(graphJson.at("/edges").isArray()).isTrue();
    }

    @Test
    void validationExtractionSkipsInfrastructureQueryTypesWhileExportingModelAttributeShape(@TempDir Path tempDir) throws IOException, IngestionException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path controllerDir = srcRoot.resolve("io/atworks/controller");
        Path dtoDir = srcRoot.resolve("io/atworks/dto");
        Files.createDirectories(controllerDir);
        Files.createDirectories(dtoDir);

        Files.writeString(controllerDir.resolve("VisitController.java"), """
            package io.atworks.controller;

            import io.atworks.dto.Pageable;
            import io.atworks.dto.VisitForm;
            import org.springframework.web.bind.annotation.ModelAttribute;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            class VisitController {
                @PostMapping("/visits")
                String createVisit(@ModelAttribute("visitForm") VisitForm visitForm, Pageable pageable) {
                    return "ok";
                }
            }
        """);
        Files.writeString(dtoDir.resolve("VisitForm.java"), """
            package io.atworks.dto;

            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.Size;
            import java.util.UUID;

            public class VisitForm {
                @Size(min = 5, max = 20)
                String description;
                OwnerSnapshot owner;
                @NotNull
                UUID requestId;
            }
        """);
        Files.writeString(dtoDir.resolve("OwnerSnapshot.java"), """
            package io.atworks.dto;

            public class OwnerSnapshot {
                Address address;
            }
        """);
        Files.writeString(dtoDir.resolve("Address.java"), """
            package io.atworks.dto;

            public class Address {
                String city;
            }
        """);
        Files.writeString(dtoDir.resolve("Pageable.java"), """
            package io.atworks.dto;

            import jakarta.validation.constraints.NotNull;

            public class Pageable {
                @NotNull
                Integer pageSize;
            }
        """);

        EndpointExtractor extractor = new EndpointExtractor(tempDir);
        List<ApiEndpoint> endpoints = extractor.extract(controllerDir.resolve("VisitController.java"));
        StaticScanResult scanResult = new StaticScanResult(
            endpoints,
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );
        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);

        ValidationExtractionService extractionService = new ValidationExtractionService();
        ValidationExtractionResult extractResult = extractionService.extract(scanResult, repositorySource);

        Map<String, EndpointRuleOutput> outputs = new RuleOutputService().generate(
            scanResult, repositorySource, extractResult.directConditions(), List.of());
        JsonNode executionJson = objectMapper.readTree(new ExecutionSpecExporter().export(
            scanResult, outputs, scanResult.warnings(), repositorySource));

        assertThat(extractResult.directConditions()).extracting(ApiConditionDraft::targetPath)
            .contains("description", "requestId")
            .doesNotContain("pageSize");
        assertThat(executionJson.at("/operations/0/request/queryParams/0/name").asText()).isEqualTo("pageable");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/owner/properties/address/properties/city/type").asText())
            .isEqualTo("string");
        assertThat(executionJson.at("/operations/0/requestPreconditions").toString())
            .contains("$.description")
            .contains("$.requestId")
            .doesNotContain("pageSize");
    }

    @Test
    void endpointExtractorUnwrapsReactiveTypesAndUsesExplicitPathVariableNames(@TempDir Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path controllerDir = srcRoot.resolve("io/atworks/controller");
        Files.createDirectories(controllerDir);
        Path controllerFile = controllerDir.resolve("VisitController.java");
        Files.writeString(controllerFile, """
            package io.atworks.controller;

            import org.springframework.http.ResponseEntity;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.RestController;
            import reactor.core.publisher.Flux;
            import reactor.core.publisher.Mono;

            @RestController
            class VisitController {
                @GetMapping("/owners/{ownerId}")
                Mono<ResponseEntity<VisitResponse>> getOwner(@PathVariable("ownerId") Long id) {
                    return Mono.empty();
                }

                @GetMapping("/vets")
                Flux<VisitResponse> getVets() {
                    return Flux.empty();
                }
            }

            record VisitResponse(String name) {}
        """);

        EndpointExtractor extractor = new EndpointExtractor(tempDir);
        List<ApiEndpoint> endpoints = extractor.extract(controllerFile);

        assertThat(endpoints).hasSize(2);
        assertThat(endpoints.get(0).requestBindings().get(0).parameterName()).isEqualTo("ownerId");
        assertThat(endpoints.get(0).requestBindings().get(0).targetLocation()).isEqualTo(BindingLocation.PATH);
        assertThat(endpoints.get(0).responseBinding().type()).isEqualTo("VisitResponse");
        assertThat(endpoints.get(1).responseBinding().type()).isEqualTo("List<VisitResponse>");
    }

    @Test
    void executionExportDistinguishesMvcViewsFromBodyResponses(@TempDir Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path controllerDir = srcRoot.resolve("io/atworks/controller");
        Files.createDirectories(controllerDir);
        Path controllerFile = controllerDir.resolve("PageController.java");
        Files.writeString(controllerFile, """
            package io.atworks.controller;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.ResponseBody;

            @Controller
            class PageController {
                @GetMapping("/dashboard")
                String dashboard() {
                    return "dashboard";
                }

                @GetMapping("/health")
                @ResponseBody
                String health() {
                    return "ok";
                }
            }
        """);

        EndpointExtractor extractor = new EndpointExtractor(tempDir);
        List<ApiEndpoint> endpoints = extractor.extract(controllerFile);
        StaticScanResult scanResult = new StaticScanResult(
            endpoints,
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );

        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);
        ExecutionSpecExporter exporter = new ExecutionSpecExporter();

        JsonNode executionJson = objectMapper.readTree(exporter.export(
            scanResult, Map.of(), scanResult.warnings(), repositorySource));

        assertThat(executionJson.at("/operations/0/response200/contentType").isNull()).isTrue();
        assertThat(executionJson.at("/operations/0/response200/schema").isNull()).isTrue();

        assertThat(executionJson.at("/operations/1/response200/contentType").asText()).isEqualTo("text/plain");
        assertThat(executionJson.at("/operations/1/response200/schema/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/1/response200/example").asText()).isEqualTo("");
    }

    @Test
    void executionExportKeepsModelAttributeStructuredBodySchemaAtDtoRoot(@TempDir Path tempDir) throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path dtoDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("OrderRequest.java"), """
            package io.atworks.order;

            import java.util.List;

            public class OrderRequest {
                private List<OrderProduct> orderProducts;
                private OrdererMemberId ordererMemberId;
                private ShippingInfo shippingInfo;

                static class OrderProduct {
                    private Long productId;
                    private int quantity;
                }

                static class OrdererMemberId {
                    private Long id;
                }

                static class ShippingInfo {
                    private Address address;
                    private Receiver receiver;
                    private String message;
                }

                static class Address {
                    private String zipCode;
                    private String address1;
                    private String address2;
                }

                static class Receiver {
                    private String name;
                    private String phone;
                }
            }
        """);

        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/order/OrderController.java", 10, 15);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/orders/order",
            "io.atworks.order.OrderController",
            "submit",
            List.of(new RequestBinding("orderRequest", BindingLocation.BODY, "OrderRequest", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        StaticScanResult scanResult = new StaticScanResult(
            List.of(endpoint),
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );

        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);
        ExecutionSpecExporter exporter = new ExecutionSpecExporter();

        JsonNode executionJson = objectMapper.readTree(exporter.export(
            scanResult, Map.of(), scanResult.warnings(), repositorySource));

        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/orderProducts/items/properties/productId/type").asText()).isEqualTo("integer");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/ordererMemberId/properties/id/type").asText()).isEqualTo("integer");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/shippingInfo/properties/address/properties/zipCode/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/shippingInfo/properties/receiver/properties/name/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/orderRequest").isMissingNode()).isTrue();
    }

    @Test
    void executionExportIncludesLateServiceHintWarnings(@TempDir Path tempDir) throws Exception {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/order/OrderController.java", 10, 15);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/orders/order",
            "io.atworks.order.OrderController",
            "submit",
            List.of(new RequestBinding("orderRequest", BindingLocation.BODY, "OrderRequest", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        StaticScanResult scanResult = new StaticScanResult(
            List.of(endpoint),
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );
        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);
        ExecutionSpecExporter exporter = new ExecutionSpecExporter();

        JsonNode executionJson = objectMapper.readTree(exporter.export(
            scanResult,
            Map.of(),
            List.of(
                new io.atworks.specscan.ingestion.domain.IngestionWarning(
                    "SERVICE_HINT_REJECTED",
                    "Skipped service hint without graph evidence for POST /orders/order: orderService.submit(orderRequest)",
                    "/orders/order",
                    "MEDIUM",
                    java.util.Map.of(
                        "endpoint", "POST /orders/order",
                        "candidateId", "cand-1",
                        "targetPath", "shippingInfo.address.zipCode",
                        "reasonCategory", "UNREACHABLE_GRAPH_EVIDENCE"
                    )
                ),
                new io.atworks.specscan.ingestion.domain.IngestionWarning(
                    "SERVICE_HINT_AMBIGUOUS",
                    "Skipped ambiguous service hint for POST /orders/order: orderService.submit(orderRequest)",
                    "/orders/order",
                    "MEDIUM",
                    java.util.Map.of(
                        "endpoint", "POST /orders/order",
                        "candidateId", "cand-2",
                        "targetPath", "orderProducts[*].productId",
                        "reasonCategory", "AMBIGUOUS_GRAPH_EVIDENCE"
                    )
                )
            ),
            repositorySource
        ));

        assertThat(executionJson.at("/warningCount").asInt()).isEqualTo(2);
        assertThat(executionJson.at("/warnings/0/code").asText()).isEqualTo("SERVICE_HINT_REJECTED");
        assertThat(executionJson.at("/warnings/0/location").asText()).isEqualTo("/orders/order");
        assertThat(executionJson.at("/warnings/0/message").asText()).contains("Skipped service hint without graph evidence");
        assertThat(executionJson.at("/warnings/0/details/reasonCategory").asText()).isEqualTo("UNREACHABLE_GRAPH_EVIDENCE");
        assertThat(executionJson.at("/warnings/0/details/candidateId").asText()).isEqualTo("cand-1");
        assertThat(executionJson.at("/warnings/0/details/endpoint").asText()).isEqualTo("POST /orders/order");
        assertThat(executionJson.at("/warnings/1/code").asText()).isEqualTo("SERVICE_HINT_AMBIGUOUS");
        assertThat(executionJson.at("/warnings/1/message").asText()).contains("Skipped ambiguous service hint");
        assertThat(executionJson.at("/warnings/1/details/reasonCategory").asText()).isEqualTo("AMBIGUOUS_GRAPH_EVIDENCE");
        assertThat(executionJson.at("/warnings/1/details/endpoint").asText()).isEqualTo("POST /orders/order");
        assertThat(executionJson.at("/warnings/1/details/targetPath").asText()).isEqualTo("orderProducts[*].productId");
    }

    @Test
    void assembledYamlUsesStructuredBodySchemaAndOmitsMvcViewContent(@TempDir Path tempDir) throws Exception {
        Path outputPath = tempDir.resolve("openapi.yaml");
        Path srcRoot = tempDir.resolve("src/main/java");
        Path dtoDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("OrderRequest.java"), """
            package io.atworks.order;

            import java.util.List;

            public class OrderRequest {
                private List<OrderProduct> orderProducts;
                private ShippingInfo shippingInfo;

                static class OrderProduct {
                    private Long productId;
                    private int quantity;
                }

                static class ShippingInfo {
                    private Receiver receiver;
                }

                static class Receiver {
                    private String name;
                }
            }
        """);

        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/order/OrderController.java", 10, 15);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/orders/order",
            "io.atworks.order.OrderController",
            "submit",
            List.of(new RequestBinding("orderRequest", BindingLocation.BODY, "OrderRequest", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        StaticScanResult scanResult = new StaticScanResult(
            List.of(endpoint),
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );
        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);

        assemblyService.assemble(
            scanResult,
            new ValidationExtractionResult(List.of(), List.of(), List.of()),
            repositorySource,
            outputPath
        );

        String yamlContent = Files.readString(outputPath);
        assertThat(yamlContent)
            .contains("/orders/order:")
            .contains("requestBody:")
            .contains("orderProducts:")
            .contains("items:")
            .contains("productId:")
            .contains("shippingInfo:")
            .contains("receiver:")
            .contains("name:")
            .doesNotContain("__mvc_view__")
            .doesNotContain("#/components/schemas")
            .contains("responses:\n        '200':\n          description: Success\n")
            .doesNotContain("responses:\n        '200':\n          description: Success\n          content:");
    }
    @Test
    void executionExportScopesNormalizedConditionsToOwningEndpoint(@TempDir Path tempDir) throws Exception {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/controller/VisitController.java", 10, 15);
        ApiEndpoint shipping = new ApiEndpoint(
            "POST",
            "/admin/orders/{orderNo}/shipping",
            "io.atworks.controller.AdminOrderController",
            "startShipping",
            List.of(
                new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace),
                new RequestBinding("version", BindingLocation.QUERY, "long", true, null, null, null, List.of(), trace)
            ),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        ApiEndpoint cancel = new ApiEndpoint(
            "GET",
            "/my/orders/{orderNo}/cancel",
            "io.atworks.controller.CancelOrderController",
            "cancel",
            List.of(new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        StaticScanResult scanResult = new StaticScanResult(
            List.of(shipping, cancel),
            2,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 2, "BRANCH", "main", 0)
        );
        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);
        ExecutionSpecExporter exporter = new ExecutionSpecExporter();

        List<ApiCondition> conditions = List.of(
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.QUERY,
                "$.version",
                "OPTIMISTIC_LOCK_MATCH",
                "must match current resource version",
                "matchVersion(req.getVersion())",
                0.5,
                "test",
                trace,
                "/admin/orders/{orderNo}/shipping"
            ),
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.AUTH,
                "$.currentUser",
                "HAS_CANCELLATION_PERMISSION",
                "orderer or ROLE_ADMIN",
                "hasCancellationPermission(order, canceller)",
                0.5,
                "test",
                trace,
                "/my/orders/{orderNo}/cancel"
            )
        );

        Map<String, EndpointRuleOutput> outputs = new RuleOutputService().generate(
            scanResult, repositorySource, List.of(), conditions);
        JsonNode executionJson = objectMapper.readTree(exporter.export(
            scanResult, outputs, scanResult.warnings(), repositorySource));

        assertThat(executionJson.at("/operations/0/requestPreconditions").toString()).doesNotContain("$.version");
        assertThat(executionJson.at("/operations/0/requestPreconditions").toString()).doesNotContain("OPTIMISTIC_LOCK_MATCH");
        assertThat(executionJson.at("/operations/1/requestPreconditions").toString()).doesNotContain("HAS_CANCELLATION_PERMISSION");
        assertThat(executionJson.at("/operations/1/requestPreconditions").toString()).doesNotContain("$.version");

        assertThat(executionJson.at("/operations/0/responseAssertions")).isEmpty();
        assertThat(executionJson.at("/operations/1/responseAssertions")).isEmpty();

        assertThat(executionJson.at("/operations/0/excludedBusinessRules").toString()).contains("$.version");
        assertThat(executionJson.at("/operations/0/excludedBusinessRules").toString()).contains("OPTIMISTIC_LOCK_MATCH");
        assertThat(executionJson.at("/operations/1/excludedBusinessRules").toString()).contains("HAS_CANCELLATION_PERMISSION");
    }

    @Test
    void representativeDddStart2RegressionPreservesOrderShapesAndEndpointScopedConditions(@TempDir Path tempDir) throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path dtoDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("OrderRequest.java"), """
            package io.atworks.order;

            import java.util.List;

            public class OrderRequest {
                private List<OrderProduct> orderProducts;
                private OrdererMemberId ordererMemberId;
                private ShippingInfo shippingInfo;

                static class OrderProduct {
                    private Long productId;
                    private int quantity;
                }

                static class OrdererMemberId {
                    private Long id;
                }

                static class ShippingInfo {
                    private Address address;
                    private Receiver receiver;
                    private String message;
                }

                static class Address {
                    private String zipCode;
                    private String address1;
                    private String address2;
                }

                static class Receiver {
                    private String name;
                    private String phone;
                }
            }
        """);

        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/order/OrderController.java", 10, 20);
        ApiEndpoint orderConfirm = new ApiEndpoint(
            "POST",
            "/orders/orderConfirm",
            "io.atworks.order.OrderController",
            "orderConfirm",
            List.of(new RequestBinding("orderRequest", BindingLocation.BODY, "OrderRequest", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        ApiEndpoint order = new ApiEndpoint(
            "POST",
            "/orders/order",
            "io.atworks.order.OrderController",
            "submit",
            List.of(new RequestBinding("orderRequest", BindingLocation.BODY, "OrderRequest", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        ApiEndpoint shipping = new ApiEndpoint(
            "POST",
            "/admin/orders/{orderNo}/shipping",
            "io.atworks.controller.AdminOrderController",
            "startShipping",
            List.of(
                new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace),
                new RequestBinding("version", BindingLocation.QUERY, "long", true, null, null, null, List.of(), trace)
            ),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        ApiEndpoint cancel = new ApiEndpoint(
            "GET",
            "/my/orders/{orderNo}/cancel",
            "io.atworks.controller.CancelOrderController",
            "cancel",
            List.of(new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("__mvc_view__", trace),
            trace
        );
        StaticScanResult scanResult = new StaticScanResult(
            List.of(orderConfirm, order, shipping, cancel),
            4,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 4, "BRANCH", "main", 0)
        );
        RepositorySource repositorySource = buildRepositorySource(tempDir, scanResult);
        ExecutionSpecExporter exporter = new ExecutionSpecExporter();

        List<ApiCondition> conditions = List.of(
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.BODY,
                "$.orderProducts",
                "NOT_EMPTY",
                "true",
                "orderProducts must not be empty",
                0.8,
                "test",
                trace,
                "/orders/order"
            ),
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.BODY,
                "$.orderProducts[*].productId",
                "REQUIRED",
                "true",
                "productId required",
                0.8,
                "test",
                trace,
                "/orders/order"
            ),
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.BODY,
                "$.shippingInfo.receiver.name",
                "NOT_BLANK",
                "true",
                "receiver name required",
                0.8,
                "test",
                trace,
                "/orders/order"
            ),
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.QUERY,
                "$.version",
                "OPTIMISTIC_LOCK_MATCH",
                "must match current resource version",
                "matchVersion(req.getVersion())",
                0.5,
                "test",
                trace,
                "/admin/orders/{orderNo}/shipping"
            ),
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.AUTH,
                "$.currentUser",
                "HAS_CANCELLATION_PERMISSION",
                "orderer or ROLE_ADMIN",
                "hasCancellationPermission(order, canceller)",
                0.5,
                "test",
                trace,
                "/my/orders/{orderNo}/cancel"
            ),
            new ApiCondition(
                io.atworks.specscan.analysis.domain.ConditionLocation.RESOURCE,
                "$.order.state",
                "STATE_IN",
                "PAYMENT_WAITING,PREPARING",
                "if (!isNotYetShipped()) throw new AlreadyShippedException();",
                0.5,
                "test",
                trace,
                "/my/orders/{orderNo}/cancel"
            )
        );

        Map<String, EndpointRuleOutput> outputs = new RuleOutputService().generate(
            scanResult, repositorySource, List.of(), conditions);
        JsonNode executionJson = objectMapper.readTree(exporter.export(
            scanResult, outputs, scanResult.warnings(), repositorySource));

        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/orderProducts/items/properties/productId/type").asText()).isEqualTo("integer");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/shippingInfo/properties/receiver/properties/name/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/orderRequest").isMissingNode()).isTrue();
        assertThat(executionJson.at("/operations/1/request/bodySchema/properties/orderProducts/items/properties/productId/type").asText()).isEqualTo("integer");
        assertThat(executionJson.at("/operations/1/request/bodySchema/properties/ordererMemberId/properties/id/type").asText()).isEqualTo("integer");
        assertThat(executionJson.at("/operations/1/request/bodySchema/properties/shippingInfo/properties/address/properties/zipCode/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/1/requestPreconditions").toString())
            .contains("$.orderProducts")
            .contains("$.orderProducts[*].productId")
            .contains("$.shippingInfo.receiver.name");
        assertThat(executionJson.at("/operations/2/requestPreconditions").toString())
            .doesNotContain("OPTIMISTIC_LOCK_MATCH");
        assertThat(executionJson.at("/operations/3/requestPreconditions").toString())
            .doesNotContain("HAS_CANCELLATION_PERMISSION")
            .doesNotContain("STATE_IN");

        assertThat(executionJson.at("/operations/1/responseAssertions")).isEmpty();
        assertThat(executionJson.at("/operations/2/responseAssertions")).isEmpty();
        assertThat(executionJson.at("/operations/3/responseAssertions")).isEmpty();

        assertThat(executionJson.at("/operations/2/excludedBusinessRules").toString())
            .contains("OPTIMISTIC_LOCK_MATCH");
        assertThat(executionJson.at("/operations/3/excludedBusinessRules").toString())
            .contains("HAS_CANCELLATION_PERMISSION")
            .contains("STATE_IN");
    }

    private RepositorySource buildRepositorySource(Path tempDir, StaticScanResult scanResult) {
        RepositoryIdentity identity = new RepositoryIdentity("github.com", "owner", "repo",
            "https://github.com/owner/repo.git", "main");
        WorkspaceContext workspace = new WorkspaceContext("exec-123", tempDir.toAbsolutePath().toString(),
            Instant.now(), "cache-key", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, 1, 1, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(1, 1, 1, true, 0);
        return new RepositorySource(
            identity,
            workspace,
            sourceRoots,
            "Gradle",
            javaSummary,
            List.of(),
            List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            scanResult.metadata()
        );
    }
}
