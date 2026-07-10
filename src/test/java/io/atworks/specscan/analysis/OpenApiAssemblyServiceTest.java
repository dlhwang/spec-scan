package io.atworks.specscan.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.ResponseBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
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
        RequestBinding bodyBinding = new RequestBinding("request", BindingLocation.BODY, "VisitRequest", true, null, null, null, List.of(), dummyTrace);
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

        ApiConditionDraft draft1 = new ApiConditionDraft("description", "SIZE", "min=5, max=20", "@Size", dummyTrace);
        ApiConditionDraft draft2 = new ApiConditionDraft("requestId", "NOT_NULL", "true", "@NotNull", dummyTrace);
        ValidationCandidate candidate1 = new ValidationCandidate("cand-1", "SERVICE_HINT", "petId", "validated", 1.0, dummyTrace);
        ValidationCandidate candidate2 = new ValidationCandidate("cand-2", "SERVICE_HINT", "petId", "validated", 1.0, dummyTrace);
        ValidationCandidate candidate3 = new ValidationCandidate("cand-3", "SERVICE_HINT", "unknownField", "validated", 1.0, dummyTrace);

        ValidationExtractionResult extractResult = new ValidationExtractionResult(
            List.of(draft1, draft2),
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
            .contains("/owners/*/pets/{petId}/visits:");

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

        JsonNode validationConditions = executionJson.at("/operations/0/validationConditions");
        assertThat(validationConditions).hasSize(2);
        assertThat(validationConditions.toString()).contains("$.description");
        assertThat(validationConditions.toString()).contains("$.requestId");
        assertThat(validationConditions.toString()).doesNotContain("$.petId");
        assertThat(validationConditions.toString()).doesNotContain("unknownField");
        assertThat(graphJson.at("/nodes").isArray()).isTrue();
        assertThat(graphJson.at("/edges").isArray()).isTrue();
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

        JsonNode executionJson = objectMapper.readTree(exporter.export(scanResult, List.of(), List.of(), repositorySource));

        assertThat(executionJson.at("/operations/0/response200/contentType").isNull()).isTrue();
        assertThat(executionJson.at("/operations/0/response200/schema").isNull()).isTrue();

        assertThat(executionJson.at("/operations/1/response200/contentType").asText()).isEqualTo("text/plain");
        assertThat(executionJson.at("/operations/1/response200/schema/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/1/response200/example").asText()).isEqualTo("");
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
