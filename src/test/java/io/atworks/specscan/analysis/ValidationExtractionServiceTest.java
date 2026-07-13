package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationExtractionServiceTest {

    private ValidationExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new ValidationExtractionService();
    }

    @Test
    void testValidationExtractionPipeline(@TempDir Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(srcRoot);

        Path dtoDir = srcRoot.resolve("io/atworks/dto");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("UserDto.java"), """
            package io.atworks.dto;
            import jakarta.validation.constraints.*;
            import io.atworks.validator.ValidEmail;

            public class UserDto {
                @NotNull
                private String username;

                @Size(min = 5, max = 20)
                private String nickname;

                @ValidEmail
                private String email;

                private int age;
            }
        """);

        Path valDir = srcRoot.resolve("io/atworks/validator");
        Files.createDirectories(valDir);
        Files.writeString(valDir.resolve("ValidEmail.java"), """
            package io.atworks.validator;
            import jakarta.validation.Constraint;
            import java.lang.annotation.*;

            @Constraint(validatedBy = EmailValidator.class)
            @Target({ElementType.FIELD})
            @Retention(RetentionPolicy.RUNTIME)
            public @interface ValidEmail {
                String message() default "Invalid email";
            }
        """);

        Files.writeString(valDir.resolve("EmailValidator.java"), """
            package io.atworks.validator;
            import jakarta.validation.ConstraintValidator;
            import jakarta.validation.ConstraintValidatorContext;

            public class EmailValidator implements ConstraintValidator<ValidEmail, String> {
                @Override
                public boolean isValid(String value, ConstraintValidatorContext context) {
                    if (value == null) return false;
                    return value.contains("@") && value.endsWith(".com");
                }
            }
        """);

        Path svcDir = srcRoot.resolve("io/atworks/service");
        Files.createDirectories(svcDir);
        Files.writeString(svcDir.resolve("UserService.java"), """
            package io.atworks.service;
            import io.atworks.dto.UserDto;

            public class UserService {
                public void register(UserDto user) {
                    if (user.getAge() < 19) {
                        throw new IllegalArgumentException("Underage is not allowed");
                    }
                }
            }
        """);

        Path ctrlDir = srcRoot.resolve("io/atworks/controller");
        Files.createDirectories(ctrlDir);
        Files.writeString(ctrlDir.resolve("UserController.java"), """
            package io.atworks.controller;
            import org.springframework.web.bind.annotation.*;
            import io.atworks.dto.UserDto;
            import io.atworks.service.UserService;

            @RestController
            @RequestMapping("/users")
            public class UserController {
                private UserService userService;

                @PostMapping
                public void createUser(@RequestBody UserDto user) {
                    userService.register(user);
                }
            }
        """);

        SourceTrace dummyTrace = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 15);
        RequestBinding bodyBinding = new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), dummyTrace);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(bodyBinding),
            new ResponseBinding("void", dummyTrace),
            dummyTrace
        );

        ValidationExtractionResult result = extractionService.extract(buildScanResult(endpoint), buildRepositorySource(tempDir, 5));

        List<ApiConditionDraft> drafts = result.directConditions();
        assertThat(drafts).hasSize(2);

        ApiConditionDraft notNullDraft = drafts.stream().filter(d -> d.operator().equals("NOT_NULL")).findFirst().orElseThrow();
        assertThat(notNullDraft.targetPath()).isEqualTo("username");

        ApiConditionDraft sizeDraft = drafts.stream().filter(d -> d.operator().equals("SIZE")).findFirst().orElseThrow();
        assertThat(sizeDraft.targetPath()).isEqualTo("nickname");
        assertThat(sizeDraft.expected()).contains("min=5").contains("max=20");

        List<ValidationCandidate> candidates = result.candidates();
        assertThat(candidates).hasSize(3);

        ValidationCandidate annCand = candidates.stream().filter(c -> c.sourceType().equals("CUSTOM_ANNOTATION")).findFirst().orElseThrow();
        assertThat(annCand.targetPath()).isEqualTo("email");
        assertThat(annCand.evidenceSnippet()).contains("@ValidEmail");
        assertThat(annCand.confidence()).isEqualTo(1.0);

        ValidationCandidate valCand = candidates.stream().filter(c -> c.sourceType().equals("VALIDATOR")).findFirst().orElseThrow();
        assertThat(valCand.targetPath()).isEqualTo("email");
        assertThat(valCand.evidenceSnippet()).contains("EmailValidator.isValid()");
        assertThat(valCand.confidence()).isEqualTo(1.0);

        ValidationCandidate svcCand = candidates.stream().filter(c -> c.sourceType().equals("SERVICE_HINT")).findFirst().orElseThrow();
        assertThat(svcCand.targetPath()).isEqualTo("age");
        assertThat(svcCand.evidenceSnippet()).contains("throw new IllegalArgumentException");
        assertThat(svcCand.confidence()).isEqualTo(0.5);
    }

    @Test
    void modelAttributeServiceHintPreservesStructuredTargetIdentity(@TempDir Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path dtoDir = srcRoot.resolve("io/atworks/order");
        Path serviceDir = srcRoot.resolve("io/atworks/order");
        Path controllerDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(dtoDir);
        Files.createDirectories(serviceDir);
        Files.createDirectories(controllerDir);

        Files.writeString(dtoDir.resolve("OrderRequest.java"), """
            package io.atworks.order;

            public class OrderRequest {
                private ShippingInfo shippingInfo;
                private OrdererMemberId ordererMemberId;

                static class ShippingInfo {
                    private Address address;
                }

                static class Address {
                    private String zipCode;
                    private String address1;
                }

                static class OrdererMemberId {
                    private Long id;
                }
            }
        """);

        Files.writeString(serviceDir.resolve("OrderService.java"), """
            package io.atworks.order;

            public class OrderService {
                public void submit(OrderRequest orderRequest) {
                    if (orderRequest.getShippingInfo().getAddress().getZipCode() == null) {
                        throw new IllegalArgumentException("zipCode required");
                    }
                }
            }
        """);

        Files.writeString(controllerDir.resolve("OrderController.java"), """
            package io.atworks.order;

            import org.springframework.web.bind.annotation.ModelAttribute;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.stereotype.Controller;

            @Controller
            public class OrderController {
                private OrderService orderService;

                @PostMapping("/orders/order")
                public String submit(@ModelAttribute("orderRequest") OrderRequest orderRequest) {
                    orderService.submit(orderRequest);
                    return "order/complete";
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

        ValidationExtractionResult result = extractionService.extract(buildScanResult(endpoint), buildRepositorySource(tempDir, 3));

        ValidationCandidate serviceHint = result.candidates().stream()
            .filter(candidate -> candidate.sourceType().equals("SERVICE_HINT"))
            .findFirst()
            .orElseThrow();

        assertThat(serviceHint.targetPath()).isEqualTo("shippingInfo.address.zipCode");
        assertThat(serviceHint.targetPath()).doesNotStartWith("orderRequest.");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void optionalLookupServiceHintResolvesEquivalentPathParameterName(@TempDir Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path packageDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(packageDir);

        Files.writeString(packageDir.resolve("StartShippingRequest.java"), """
            package io.atworks.order;

            public class StartShippingRequest {
                public String getOrderNumber() {
                    return null;
                }
            }
        """);

        Files.writeString(packageDir.resolve("Order.java"), """
            package io.atworks.order;
            public class Order {}
        """);

        Files.writeString(packageDir.resolve("OrderNo.java"), """
            package io.atworks.order;
            public class OrderNo {
                public OrderNo(String value) {}
            }
        """);

        Files.writeString(packageDir.resolve("OrderRepository.java"), """
            package io.atworks.order;
            import java.util.Optional;
            public interface OrderRepository {
                Optional<Order> findById(OrderNo orderNo);
            }
        """);

        Files.writeString(packageDir.resolve("ShippingService.java"), """
            package io.atworks.order;
            import java.util.Optional;
            public class ShippingService {
                private OrderRepository orderRepository;
                public void startShipping(StartShippingRequest req) {
                    Optional<Order> orderOpt = orderRepository.findById(new OrderNo(req.getOrderNumber()));
                    orderOpt.orElseThrow(() -> new IllegalArgumentException("missing order"));
                }
            }
        """);

        Files.writeString(packageDir.resolve("OrderController.java"), """
            package io.atworks.order;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.stereotype.Controller;
            @Controller
            public class OrderController {
                private ShippingService shippingService;
                @PostMapping("/admin/orders/{orderNo}/shipping")
                public String startShipping(@PathVariable String orderNo) {
                    shippingService.startShipping(new StartShippingRequest());
                    return "ok";
                }
            }
        """);

        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/order/OrderController.java", 10, 15);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/admin/orders/{orderNo}/shipping",
            "io.atworks.order.OrderController",
            "startShipping",
            List.of(new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("String", trace),
            trace
        );

        ValidationExtractionResult result = extractionService.extract(buildScanResult(endpoint), buildRepositorySource(tempDir, 6));

        ValidationCandidate serviceHint = result.candidates().stream()
            .filter(candidate -> candidate.sourceType().equals("SERVICE_HINT"))
            .filter(candidate -> candidate.evidenceSnippet().contains("orElseThrow"))
            .findFirst()
            .orElseThrow();

        assertThat(serviceHint.targetPath()).isEqualTo("orderNo");
        assertThat(serviceHint.evidenceSnippet()).contains("findById");
        assertThat(result.warnings()).isEmpty();
    }


    @Test
    void ambiguousStructuredServiceHintIsRejected(@TempDir Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path packageDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(packageDir);

        Files.writeString(packageDir.resolve("OrderRequest.java"), """
            package io.atworks.order;

            public class OrderRequest {
                private Address shippingAddress;
                private Address billingAddress;

                static class Address {
                    private String zipCode;
                }
            }
        """);

        Files.writeString(packageDir.resolve("OrderService.java"), """
            package io.atworks.order;

            public class OrderService {
                public void submit(String zipCode) {
                    if (zipCode == null) {
                        throw new IllegalArgumentException("zipCode required");
                    }
                }
            }
        """);

        Files.writeString(packageDir.resolve("OrderController.java"), """
            package io.atworks.order;

            import org.springframework.web.bind.annotation.ModelAttribute;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.stereotype.Controller;

            @Controller
            public class OrderController {
                private OrderService orderService;

                @PostMapping("/orders/order")
                public String submit(@ModelAttribute("orderRequest") OrderRequest orderRequest) {
                    orderService.submit(orderRequest.getShippingAddress().getZipCode());
                    return "order/complete";
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

        ValidationExtractionResult result = extractionService.extract(buildScanResult(endpoint), buildRepositorySource(tempDir, 3));

        assertThat(result.candidates()).noneMatch(candidate -> candidate.sourceType().equals("SERVICE_HINT"));
        assertThat(result.warnings()).anyMatch(warning ->
            warning.warningCode().equals("SERVICE_HINT_AMBIGUOUS")
                && "AMBIGUOUS_GRAPH_EVIDENCE".equals(warning.details().get("reasonCategory"))
        );
    }

    private StaticScanResult buildScanResult(ApiEndpoint endpoint) {
        return new StaticScanResult(
            List.of(endpoint),
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );
    }

    private RepositorySource buildRepositorySource(Path tempDir, int fileCount) {
        RepositoryIdentity identity = new RepositoryIdentity("github.com", "owner", "repo", "https://github.com/owner/repo.git", "main");
        WorkspaceContext workspace = new WorkspaceContext("exec-123", tempDir.toAbsolutePath().toString(), Instant.now(), "cache-key", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, fileCount, fileCount, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(fileCount, 1, 1, true, 0);
        return new RepositorySource(
            identity,
            workspace,
            sourceRoots,
            "Gradle",
            javaSummary,
            List.of(),
            List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(Instant.now(), Instant.now(), fileCount, "BRANCH", "main", 0)
        );
    }
}
