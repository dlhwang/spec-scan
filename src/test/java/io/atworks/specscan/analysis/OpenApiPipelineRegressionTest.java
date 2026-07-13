package io.atworks.specscan.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.ingestion.domain.IngestionMetadata;
import io.atworks.specscan.ingestion.domain.JavaInventorySummary;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.SafetyPolicyHint;
import io.atworks.specscan.ingestion.domain.SourceRootCandidate;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiPipelineRegressionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dddStart2RepresentativeRegressionExercisesFullAssemblyPipeline(@TempDir Path tempDir) throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path packageDir = srcRoot.resolve("io/atworks/dddstart2");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("OrderRequest.java"), """
            package io.atworks.dddstart2;

            import jakarta.validation.Valid;
            import jakarta.validation.constraints.NotBlank;
            import jakarta.validation.constraints.NotEmpty;
            import jakarta.validation.constraints.NotNull;
            import java.util.List;

            public class OrderRequest {
                @NotEmpty
                private List<@Valid OrderProduct> orderProducts;
                @Valid
                private OrdererMemberId ordererMemberId;
                @Valid
                private ShippingInfo shippingInfo;

                public List<OrderProduct> getOrderProducts() {
                    return orderProducts;
                }

                public OrdererMemberId getOrdererMemberId() {
                    return ordererMemberId;
                }

                public ShippingInfo getShippingInfo() {
                    return shippingInfo;
                }

                static class OrderProduct {
                    @NotNull
                    private Long productId;
                    @NotNull
                    private Integer quantity;

                    public Long getProductId() {
                        return productId;
                    }
                }

                static class OrdererMemberId {
                    @NotNull
                    private Long id;

                    public Long getId() {
                        return id;
                    }
                }

                static class ShippingInfo {
                    @Valid
                    private Address address;
                    @Valid
                    private Receiver receiver;
                    private String message;

                    public Address getAddress() {
                        return address;
                    }

                    public Receiver getReceiver() {
                        return receiver;
                    }
                }

                static class Address {
                    @NotBlank
                    private String zipCode;
                    @NotBlank
                    private String address1;
                    private String address2;

                    public String getZipCode() {
                        return zipCode;
                    }
                }

                static class Receiver {
                    @NotBlank
                    private String name;
                    @NotBlank
                    private String phone;

                    public String getName() {
                        return name;
                    }
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderMvcController.java"), """
            package io.atworks.dddstart2;

            import org.springframework.stereotype.Controller;
            import org.springframework.web.bind.annotation.ModelAttribute;
            import org.springframework.web.bind.annotation.PostMapping;

            @Controller
            public class OrderMvcController {
                private OrderConfirmService orderConfirmService;
                private SubmitService submitService;

                @PostMapping("/orders/orderConfirm")
                public String orderConfirm(@ModelAttribute("orderRequest") OrderRequest orderRequest) {
                    orderConfirmService.orderConfirm(orderRequest);
                    return "order/confirm";
                }

                @PostMapping("/orders/order")
                public String submit(@ModelAttribute("orderRequest") OrderRequest orderRequest) {
                    submitService.submit(orderRequest);
                    return "order/complete";
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderConfirmService.java"), """
            package io.atworks.dddstart2;

            public class OrderConfirmService {
                public void orderConfirm(OrderRequest orderRequest) {
                }
            }
        """);
        Files.writeString(packageDir.resolve("SubmitService.java"), """
            package io.atworks.dddstart2;

            public class SubmitService {
                public void submit(OrderRequest orderRequest) {
                    if (orderRequest.getOrderProducts().isEmpty()) {
                        throw new IllegalArgumentException("orderProducts required");
                    }
                    if (orderRequest.getOrderProducts().get(0).getProductId() == null) {
                        throw new IllegalArgumentException("productId required");
                    }
                    if (orderRequest.getShippingInfo().getReceiver().getName() == null) {
                        throw new IllegalArgumentException("receiver name required");
                    }
                    ValidationError.of("orderProducts[0].productId", "required");
                    ValidationError.of("shippingInfo.receiver.name", "required");
                }
            }
        """);
        Files.writeString(packageDir.resolve("ValidationError.java"), """
            package io.atworks.dddstart2;

            public class ValidationError {
                public static ValidationError of(String field, String code) {
                    return new ValidationError();
                }
            }
        """);
        Files.writeString(packageDir.resolve("ShippingController.java"), """
            package io.atworks.dddstart2;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class ShippingController {
                private ShippingService shippingService;
                private CancelService cancelService;

                @PostMapping("/admin/orders/{orderNo}/shipping")
                public void startShipping(@PathVariable String orderNo, @RequestParam long version) {
                    shippingService.startShipping(orderNo, version);
                }

                @GetMapping("/my/orders/{orderNo}/cancel")
                public void cancel(@PathVariable String orderNo) {
                    cancelService.cancel(orderNo, new User());
                }
            }
        """);
        Files.writeString(packageDir.resolve("ShippingService.java"), """
            package io.atworks.dddstart2;

            public class ShippingService {
                public void startShipping(String orderNo, long version) {
                    Order order = new Order();
                    if (version != order.getRequestedVersion()) {
                        throw new VersionConflictException();
                    }
                }
            }
        """);
        Files.writeString(packageDir.resolve("CancelService.java"), """
            package io.atworks.dddstart2;

            public class CancelService {
                private CancelPolicy cancelPolicy;

                public void cancel(String orderNo, User currentUser) {
                    Order order = new Order();
                    if (!cancelPolicy.hasPermission(order, currentUser)) {
                        throw new NoCancellablePermission();
                    }
                    if (order.getStatus() != OrderState.PAYMENT_WAITING && order.getStatus() != OrderState.PREPARING) {
                        throw new AlreadyShippedException();
                    }
                }
            }
        """);
        Files.writeString(packageDir.resolve("Order.java"), """
            package io.atworks.dddstart2;

            public class Order {
                private OrderState state = OrderState.PAYMENT_WAITING;
                private long requestedVersion;

                public long getRequestedVersion() {
                    return requestedVersion;
                }

                public OrderState getStatus() {
                    return state;
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderState.java"), """
            package io.atworks.dddstart2;

            public enum OrderState {
                PAYMENT_WAITING,
                PREPARING,
                SHIPPED,
                CANCELED
            }
        """);
        Files.writeString(packageDir.resolve("CancelPolicy.java"), """
            package io.atworks.dddstart2;

            public class CancelPolicy {
                public boolean hasPermission(Order order, User currentUser) {
                    return currentUser != null;
                }
            }
        """);
        Files.writeString(packageDir.resolve("User.java"), """
            package io.atworks.dddstart2;

            public class User {
            }
        """);
        Files.writeString(packageDir.resolve("VersionConflictException.java"), """
            package io.atworks.dddstart2;

            public class VersionConflictException extends RuntimeException {
            }
        """);
        Files.writeString(packageDir.resolve("NoCancellablePermission.java"), """
            package io.atworks.dddstart2;

            public class NoCancellablePermission extends RuntimeException {
            }
        """);
        Files.writeString(packageDir.resolve("AlreadyShippedException.java"), """
            package io.atworks.dddstart2;

            public class AlreadyShippedException extends RuntimeException {
            }
        """);

        RepositorySource repositorySource = buildRepositorySource(tempDir);
        StaticScanResult scanResult = new SpringStaticScanService().scan(repositorySource);
        ValidationExtractionResult extractionResult = new ValidationExtractionService().extract(scanResult, repositorySource);
        assertThat(extractionResult.candidates()).extracting(candidate -> candidate.evidenceSnippet())
            .anySatisfy(snippet -> assertThat(snippet).contains("version != order.getRequestedVersion()"))
            .anySatisfy(snippet -> assertThat(snippet).contains("hasPermission(order, currentUser)"))
            .anySatisfy(snippet -> assertThat(snippet).contains("order.getStatus() != OrderState.PAYMENT_WAITING"));

        NormalizedResult normalizedResult = new NormalizationService().normalize(
            extractionResult.candidates(),
            scanResult.endpoints(),
            new io.atworks.specscan.analysis.support.ValidationEvidenceGraphBuilder().build(scanResult, extractionResult, repositorySource)
        );
        assertThat(normalizedResult.conditions()).extracting(condition -> condition.operator())
            .contains("OPTIMISTIC_LOCK_MATCH", "HAS_CANCELLATION_PERMISSION", "STATE_IN");

        Path outputPath = tempDir.resolve("openapi.yaml");
        new OpenApiAssemblyService().assemble(scanResult, extractionResult, repositorySource, outputPath);

        JsonNode executionJson = objectMapper.readTree(Files.readString(tempDir.resolve("api-execution-model.json")));
        JsonNode orderConfirm = findOperation(executionJson, "/orders/orderConfirm");
        JsonNode order = findOperation(executionJson, "/orders/order");
        JsonNode shipping = findOperation(executionJson, "/admin/orders/{orderNo}/shipping");
        JsonNode cancel = findOperation(executionJson, "/my/orders/{orderNo}/cancel");

        assertThat(orderConfirm.at("/request/bodySchema/properties/orderProducts/items/properties/productId/type").asText()).isEqualTo("integer");
        assertThat(orderConfirm.at("/request/bodySchema/properties/shippingInfo/properties/receiver/properties/name/type").asText()).isEqualTo("string");
        assertThat(order.at("/request/bodySchema/properties/ordererMemberId/properties/id/type").asText()).isEqualTo("integer");
        assertThat(order.at("/requestPreconditions").toString())
            .contains("$.orderProducts")
            .contains("$.orderProducts[*].productId")
            .contains("$.shippingInfo.receiver.name");
        assertThat(shipping.at("/requestPreconditions").toString())
            .doesNotContain("OPTIMISTIC_LOCK_MATCH");
        assertThat(shipping.at("/responseAssertions").toString())
            .contains("STATUS");
        assertThat(shipping.at("/excludedBusinessRules").toString())
            .contains("OPTIMISTIC_LOCK_MATCH");
        assertThat(cancel.at("/requestPreconditions").toString())
            .doesNotContain("HAS_CANCELLATION_PERMISSION")
            .doesNotContain("STATE_IN");
        assertThat(cancel.at("/responseAssertions").toString())
            .contains("STATUS");
        assertThat(cancel.at("/excludedBusinessRules").toString())
            .contains("HAS_CANCELLATION_PERMISSION")
            .contains("STATE_IN");
        assertThat(executionJson.at("/warningCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(executionJson.at("/warnings/0/code").asText()).isEqualTo("SERVICE_HINT_REJECTED");
        assertThat(executionJson.at("/warnings/0/location").asText()).isEqualTo("/orders/order");
        assertThat(executionJson.at("/warnings/0/message").asText()).contains("Skipped service hint because no reachable rule qualified");
        assertThat(executionJson.at("/warnings/0/details/reasonCategory").asText()).isEqualTo("NO_QUALIFYING_RULE");
        assertThat(executionJson.at("/warnings/0/details/endpoint").asText()).isEqualTo("POST /orders/order");
        assertThat(executionJson.at("/warnings").toString())
            .contains("candidateId")
            .contains("shippingInfo.receiver.name");
    }
    private JsonNode findOperation(JsonNode executionJson, String path) {
        for (JsonNode operation : executionJson.path("operations")) {
            if (path.equals(operation.path("path").asText())) {
                return operation;
            }
        }
        throw new AssertionError("Operation not found: " + path);
    }

    private RepositorySource buildRepositorySource(Path tempDir) {
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
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );
    }
}
