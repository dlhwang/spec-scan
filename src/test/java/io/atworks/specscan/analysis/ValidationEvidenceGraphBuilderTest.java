package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.support.ValidationEvidenceGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationEvidenceGraphBuilderTest {

    private Path tempDir;
    private RepositorySource repositorySource;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("spec-scan-test");
        Path srcRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(srcRoot);

        // UserDto
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

        // ValidEmail Custom Annotation
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

        // EmailValidator ConstraintValidator
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

        // UserService
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

        // UserController
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

        // GlobalExceptionHandler
        Path adviceDir = srcRoot.resolve("io/atworks/advice");
        Files.createDirectories(adviceDir);
        Files.writeString(adviceDir.resolve("GlobalExceptionHandler.java"), """
            package io.atworks.advice;
            
            import org.springframework.http.HttpStatus;
            import org.springframework.web.bind.annotation.ExceptionHandler;
            import org.springframework.web.bind.annotation.ResponseStatus;
            import org.springframework.web.bind.annotation.RestControllerAdvice;
            
            @RestControllerAdvice
            public class GlobalExceptionHandler {
            
                @ExceptionHandler(IllegalArgumentException.class)
                @ResponseStatus(HttpStatus.BAD_REQUEST)
                public String handleIllegalArgument(IllegalArgumentException ex) {
                    return ex.getMessage();
                }
            }
        """);

        RepositoryIdentity identity = new RepositoryIdentity("test-repo", "owner", "test-repo", "test-path", "main");
        WorkspaceContext workspace = new WorkspaceContext("test-exec", tempDir.toAbsolutePath().toString(), java.time.Instant.now(), "test-cache", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, 6, 6, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(6, 1, 1, true, 0);
        repositorySource = new RepositorySource(
            identity,
            workspace,
            sourceRoots,
            "Gradle",
            javaSummary,
            List.of(),
            List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(java.time.Instant.now(), java.time.Instant.now(), 6, "LOCAL", "main", 0)
        );
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(java.io.File::delete);
        }
    }

    @Test
    public void testBuildEvidenceGraph() throws Exception {
        SpringStaticScanService scanService = new SpringStaticScanService();
        StaticScanResult scanResult = scanService.scan(repositorySource);
        assertThat(scanResult.endpoints()).hasSize(1);

        ValidationExtractionService extractionService = new ValidationExtractionService();
        ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);

        ValidationEvidenceGraphBuilder graphBuilder = new ValidationEvidenceGraphBuilder();
        ValidationEvidenceGraph graph = graphBuilder.build(scanResult, extractionResult, repositorySource);

        assertThat(graph.nodes()).isNotEmpty();
        assertThat(graph.edges()).isNotEmpty();

        // 1. Endpoint Node should exist
        boolean hasEndpointNode = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.ENDPOINT && node.id().startsWith("ENDPOINT:POST:/users"));
        assertThat(hasEndpointNode).isTrue();

        // 2. DTO Field Node should exist
        boolean hasFieldNode = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.DTO_FIELD && node.id().equals("DTO_FIELD:UserDto.username"));
        assertThat(hasFieldNode).isTrue();

        // 3. Validator Nodes should exist (both standard NotNull and custom ValidEmail)
        boolean hasNotNullValidator = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.VALIDATOR && node.id().equals("VALIDATOR:NotNull"));
        assertThat(hasNotNullValidator).isTrue();

        boolean hasCustomValidator = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.VALIDATOR && node.id().equals("VALIDATOR:ValidEmail"));
        assertThat(hasCustomValidator).isTrue();

        // 4. ConstraintValidator class node should exist
        boolean hasConstraintValClassNode = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.VALIDATOR && node.id().equals("VALIDATOR:EmailValidator"));
        assertThat(hasConstraintValClassNode).isTrue();

        // 5. Service Method Node should exist
        boolean hasServiceMethod = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.SERVICE_METHOD
                        && node.id().startsWith("SERVICE_METHOD:io.atworks.service.UserService.register("));
        assertThat(hasServiceMethod).isTrue();

        // 6. Business Rule Node (if-throw condition in Service) should exist
        boolean hasBusinessRule = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.BUSINESS_RULE
                        && node.id().startsWith("BUSINESS_RULE:io.atworks.service.UserService.register("));
        assertThat(hasBusinessRule).isTrue();

        // 7. Exception Node should exist
        boolean hasException = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.EXCEPTION && node.id().equals("EXCEPTION:IllegalArgumentException"));
        assertThat(hasException).isTrue();

        // 8. HTTP Status Node mapped via ControllerAdvice ExceptionHandler should exist
        boolean hasHttpStatus = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.HTTP_STATUS && node.id().equals("HTTP_STATUS:400 BAD_REQUEST"));
        assertThat(hasHttpStatus).isTrue();

        // 9. Edge assertions
        // Endpoint accepts DTO Field
        boolean endpointToField = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().startsWith("ENDPOINT:POST:/users") && edge.targetId().equals("DTO_FIELD:UserDto.username") && edge.type() == GraphEdgeType.ACCEPTS);
        assertThat(endpointToField).isTrue();

        // DTO Field annotated with Validator
        boolean fieldToNotNull = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().equals("DTO_FIELD:UserDto.username") && edge.targetId().equals("VALIDATOR:NotNull") && edge.type() == GraphEdgeType.ANNOTATED_WITH);
        assertThat(fieldToNotNull).isTrue();

        // Custom Annotation resolved by ConstraintValidator class
        boolean annotationToValidator = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().equals("VALIDATOR:ValidEmail") && edge.targetId().equals("VALIDATOR:EmailValidator") && edge.type() == GraphEdgeType.EVALUATES);
        assertThat(annotationToValidator).isTrue();

        // Endpoint calls Service Method
        boolean endpointToService = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().startsWith("ENDPOINT:POST:/users")
                        && edge.targetId().startsWith("SERVICE_METHOD:io.atworks.service.UserService.register(")
                        && edge.type() == GraphEdgeType.CALLS);
        assertThat(endpointToService).isTrue();

        // Service evaluates Business Rule
        boolean serviceToRule = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().startsWith("SERVICE_METHOD:io.atworks.service.UserService.register(")
                        && edge.targetId().startsWith("BUSINESS_RULE:io.atworks.service.UserService.register(")
                        && edge.type() == GraphEdgeType.EVALUATES);
        assertThat(serviceToRule).isTrue();

        // Business Rule throws Exception
        boolean ruleToException = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().startsWith("BUSINESS_RULE:io.atworks.service.UserService.register(")
                        && edge.targetId().equals("EXCEPTION:IllegalArgumentException")
                        && edge.type() == GraphEdgeType.THROWS);
        assertThat(ruleToException).isTrue();

        // Exception maps to HTTP Status
        boolean exceptionToStatus = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().equals("EXCEPTION:IllegalArgumentException") && edge.targetId().equals("HTTP_STATUS:400 BAD_REQUEST") && edge.type() == GraphEdgeType.MAPS_TO);
        assertThat(exceptionToStatus).isTrue();
    }

    @Test
    public void testBuildEvidenceGraphTraversesServiceToDomainGuards() throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");

        Path domainDir = srcRoot.resolve("io/atworks/order");
        Files.createDirectories(domainDir);
        Files.writeString(domainDir.resolve("Order.java"), """
            package io.atworks.order;

            public class Order {
                private final String status;
                private final CancelPolicy cancelPolicy;

                public Order(String status, CancelPolicy cancelPolicy) {
                    this.status = status;
                    this.cancelPolicy = cancelPolicy;
                }

                public void startShipping() {
                    if (!"PAYED".equals(status)) {
                        throw new IllegalStateException("Order is not ready for shipping");
                    }
                }

                public void cancel(User canceller) {
                    if (!cancelPolicy.hasCancellationPermission(this, canceller)) {
                        throw new IllegalArgumentException("No cancellation permission");
                    }
                }
            }
        """);
        Files.writeString(domainDir.resolve("CancelPolicy.java"), """
            package io.atworks.order;

            public class CancelPolicy {
                public boolean hasCancellationPermission(Order order, User canceller) {
                    return canceller.isAdmin();
                }
            }
        """);
        Files.writeString(domainDir.resolve("User.java"), """
            package io.atworks.order;

            public class User {
                public boolean isAdmin() {
                    return false;
                }
            }
        """);

        Path repoDir = srcRoot.resolve("io/atworks/repository");
        Files.createDirectories(repoDir);
        Files.writeString(repoDir.resolve("OrderRepository.java"), """
            package io.atworks.repository;

            import io.atworks.order.CancelPolicy;
            import io.atworks.order.Order;

            public class OrderRepository {
                public Order findById(String orderNo) {
                    return new Order("CREATED", new CancelPolicy());
                }
            }
        """);

        Path serviceDir = srcRoot.resolve("io/atworks/shipping");
        Files.createDirectories(serviceDir);
        Files.writeString(serviceDir.resolve("ShippingService.java"), """
            package io.atworks.shipping;

            import io.atworks.order.Order;
            import io.atworks.repository.OrderRepository;

            public class ShippingService {
                private OrderRepository orderRepository;

                public void startShipping(String orderNo) {
                    Order order = orderRepository.findById(orderNo);
                    order.startShipping();
                }
            }
        """);
        Files.writeString(serviceDir.resolve("CancelOrderService.java"), """
            package io.atworks.shipping;

            import io.atworks.order.Order;
            import io.atworks.order.User;
            import io.atworks.repository.OrderRepository;

            public class CancelOrderService {
                private OrderRepository orderRepository;

                public void cancel(String orderNo, User canceller) {
                    Order order = orderRepository.findById(orderNo);
                    order.cancel(canceller);
                }
            }
        """);

        Path controllerDir = srcRoot.resolve("io/atworks/shipping");
        Files.writeString(controllerDir.resolve("ShippingController.java"), """
            package io.atworks.shipping;

            import io.atworks.order.User;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class ShippingController {
                private ShippingService shippingService;
                private CancelOrderService cancelOrderService;

                @PostMapping("/admin/orders/{orderNo}/shipping")
                public void startShipping(@PathVariable String orderNo) {
                    shippingService.startShipping(orderNo);
                }

                @PostMapping("/my/orders/{orderNo}/cancel")
                public void cancel(@PathVariable String orderNo) {
                    cancelOrderService.cancel(orderNo, new User());
                }
            }
        """);

        Path adviceDir = srcRoot.resolve("io/atworks/shipping");
        Files.writeString(adviceDir.resolve("OrderExceptionHandler.java"), """
            package io.atworks.shipping;

            import org.springframework.http.HttpStatus;
            import org.springframework.web.bind.annotation.ExceptionHandler;
            import org.springframework.web.bind.annotation.ResponseStatus;
            import org.springframework.web.bind.annotation.RestControllerAdvice;

            @RestControllerAdvice
            public class OrderExceptionHandler {
                @ExceptionHandler(IllegalStateException.class)
                @ResponseStatus(HttpStatus.CONFLICT)
                public String handleIllegalState(IllegalStateException ex) {
                    return ex.getMessage();
                }
            }
        """);

        SpringStaticScanService scanService = new SpringStaticScanService();
        StaticScanResult scanResult = scanService.scan(repositorySource);

        ValidationExtractionService extractionService = new ValidationExtractionService();
        ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);

        ValidationEvidenceGraphBuilder graphBuilder = new ValidationEvidenceGraphBuilder();
        ValidationEvidenceGraph graph = graphBuilder.build(scanResult, extractionResult, repositorySource);

        assertThat(graph.nodes()).anyMatch(node -> node.id().startsWith("SERVICE_METHOD:io.atworks.shipping.ShippingService.startShipping("));
        assertThat(graph.nodes()).anyMatch(node -> node.id().startsWith("SERVICE_METHOD:io.atworks.order.Order.startShipping("));
        assertThat(graph.nodes()).anyMatch(node -> node.id().startsWith("BUSINESS_RULE:io.atworks.order.Order.startShipping(") && node.label().contains("PAYED"));
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("EXCEPTION:IllegalStateException"));
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("HTTP_STATUS:409 CONFLICT"));

        assertThat(graph.edges()).anyMatch(edge ->
            edge.sourceId().startsWith("SERVICE_METHOD:io.atworks.shipping.ShippingService.startShipping(")
                && edge.targetId().startsWith("SERVICE_METHOD:io.atworks.order.Order.startShipping(")
                && edge.type() == GraphEdgeType.CALLS
        );
        assertThat(graph.edges()).anyMatch(edge ->
            edge.sourceId().startsWith("SERVICE_METHOD:io.atworks.order.Order.startShipping(")
                && edge.targetId().startsWith("BUSINESS_RULE:io.atworks.order.Order.startShipping(")
                && edge.type() == GraphEdgeType.EVALUATES
        );
        assertThat(graph.edges()).anyMatch(edge ->
            edge.sourceId().startsWith("BUSINESS_RULE:io.atworks.order.Order.startShipping(")
                && edge.targetId().equals("EXCEPTION:IllegalStateException")
                && edge.type() == GraphEdgeType.THROWS
        );
        assertThat(graph.edges()).anyMatch(edge ->
            edge.sourceId().equals("ENDPOINT:POST:/my/orders/{orderNo}/cancel")
                && edge.targetId().startsWith("SERVICE_METHOD:io.atworks.shipping.CancelOrderService.cancel(")
                && edge.type() == GraphEdgeType.CALLS
        );
        assertThat(graph.edges()).anyMatch(edge ->
            edge.sourceId().startsWith("SERVICE_METHOD:io.atworks.shipping.CancelOrderService.cancel(")
                && edge.targetId().startsWith("SERVICE_METHOD:io.atworks.order.Order.cancel(")
                && edge.type() == GraphEdgeType.CALLS
        );
        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("BUSINESS_RULE:io.atworks.order.Order.cancel(")
                && node.label().contains("hasCancellationPermission")
        );
    }

    @Test
    public void testBuildEvidenceGraphUsesResolvedMethodSignatureForOverloads() throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");

        Path serviceDir = srcRoot.resolve("io/atworks/overload");
        Files.createDirectories(serviceDir);
        Files.writeString(serviceDir.resolve("OverloadService.java"), """
            package io.atworks.overload;

            public class OverloadService {
                public void process(String userId) {
                    validate(userId);
                }

                public void validate(String userId) {
                    if (userId.isBlank()) {
                        throw new IllegalArgumentException("blank");
                    }
                }

                public void validate(Long userId) {
                    throw new IllegalStateException("wrong overload");
                }
            }
        """);
        Files.writeString(serviceDir.resolve("OverloadController.java"), """
            package io.atworks.overload;

            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class OverloadController {
                private OverloadService overloadService;

                @PostMapping("/overload/{userId}")
                public void process(@PathVariable String userId) {
                    overloadService.process(userId);
                }
            }
        """);
        Files.writeString(serviceDir.resolve("OverloadExceptionHandler.java"), """
            package io.atworks.overload;

            import org.springframework.http.HttpStatus;
            import org.springframework.web.bind.annotation.ExceptionHandler;
            import org.springframework.web.bind.annotation.ResponseStatus;
            import org.springframework.web.bind.annotation.RestControllerAdvice;

            @RestControllerAdvice
            public class OverloadExceptionHandler {
                @ExceptionHandler(IllegalArgumentException.class)
                @ResponseStatus(HttpStatus.BAD_REQUEST)
                public String handleIllegalArgument(IllegalArgumentException ex) {
                    return ex.getMessage();
                }
            }
        """);

        SpringStaticScanService scanService = new SpringStaticScanService();
        StaticScanResult scanResult = scanService.scan(repositorySource);
        ValidationExtractionResult extractionResult = new ValidationExtractionService().extract(scanResult, repositorySource);
        ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractionResult, repositorySource);

        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("SERVICE_METHOD:io.atworks.overload.OverloadService.validate(java.lang.String)")
        );
        assertThat(graph.nodes()).noneMatch(node ->
            node.id().startsWith("SERVICE_METHOD:io.atworks.overload.OverloadService.validate(java.lang.Long)")
        );
        assertThat(graph.nodes()).anyMatch(node -> node.id().equals("EXCEPTION:IllegalArgumentException"));
        assertThat(graph.nodes()).noneMatch(node -> node.id().equals("EXCEPTION:IllegalStateException"));
    }

    @Test
    public void testBuildEvidenceGraphTraversesBeyondFixedDepthChains() throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");

        Path packageDir = srcRoot.resolve("io/atworks/depth");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("DeepController.java"), """
            package io.atworks.depth;

            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class DeepController {
                private DeepService deepService;

                @PostMapping("/deep/orders")
                public void create() {
                    deepService.start();
                }
            }
        """);
        Files.writeString(packageDir.resolve("DeepService.java"), """
            package io.atworks.depth;

            public class DeepService {
                private DeepHelper deepHelper;

                public void start() {
                    deepHelper.load();
                }
            }
        """);
        Files.writeString(packageDir.resolve("DeepHelper.java"), """
            package io.atworks.depth;

            public class DeepHelper {
                private DeepDomain deepDomain;

                public void load() {
                    deepDomain.advance();
                }
            }
        """);
        Files.writeString(packageDir.resolve("DeepDomain.java"), """
            package io.atworks.depth;

            public class DeepDomain {
                private DeepGuard deepGuard;

                public void advance() {
                    deepGuard.ensureReady();
                }
            }
        """);
        Files.writeString(packageDir.resolve("DeepGuard.java"), """
            package io.atworks.depth;

            public class DeepGuard {
                public void ensureReady() {
                    if (true) {
                        throw new IllegalArgumentException("too deep for fixed depth");
                    }
                }
            }
        """);

        SpringStaticScanService scanService = new SpringStaticScanService();
        StaticScanResult scanResult = scanService.scan(repositorySource);
        ValidationExtractionResult extractionResult = new ValidationExtractionService().extract(scanResult, repositorySource);
        ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractionResult, repositorySource);

        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("SERVICE_METHOD:io.atworks.depth.DeepService.start()")
        );
        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("SERVICE_METHOD:io.atworks.depth.DeepHelper.load()")
        );
        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("SERVICE_METHOD:io.atworks.depth.DeepDomain.advance()")
        );
        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("SERVICE_METHOD:io.atworks.depth.DeepGuard.ensureReady()")
        );
        assertThat(graph.nodes()).anyMatch(node ->
            node.id().startsWith("BUSINESS_RULE:io.atworks.depth.DeepGuard.ensureReady(")
        );
    }
    @Test
    public void testBuildEvidenceGraphClassifiesReusableRuleTypes() throws Exception {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path packageDir = srcRoot.resolve("io/atworks/classify");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("Order.java"), """
            package io.atworks.classify;

            public class Order {
                private final String status;
                private final long version;
                private final CancelPolicy cancelPolicy;

                public Order(String status, long version, CancelPolicy cancelPolicy) {
                    this.status = status;
                    this.version = version;
                    this.cancelPolicy = cancelPolicy;
                }

                public void ensureVersion(long requestedVersion) {
                    if (version != requestedVersion) {
                        throw new IllegalStateException("version mismatch");
                    }
                }

                public void ensureShippable() {
                    if (!"PAYED".equals(status)) {
                        throw new IllegalStateException("bad status");
                    }
                }

                public void cancel(User currentUser) {
                    if (!cancelPolicy.hasCancellationPermission(this, currentUser)) {
                        throw new IllegalArgumentException("no permission");
                    }
                }
            }
        """);
        Files.writeString(packageDir.resolve("CancelPolicy.java"), """
            package io.atworks.classify;

            public class CancelPolicy {
                public boolean hasCancellationPermission(Order order, User currentUser) {
                    return currentUser.isAdmin();
                }
            }
        """);
        Files.writeString(packageDir.resolve("User.java"), """
            package io.atworks.classify;

            public class User {
                public boolean isAdmin() {
                    return false;
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderRepository.java"), """
            package io.atworks.classify;

            public class OrderRepository {
                public Order findById(String orderNo) {
                    return null;
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderService.java"), """
            package io.atworks.classify;

            public class OrderService {
                private OrderRepository orderRepository;

                public void ship(String orderNo, long version) {
                    Order order = orderRepository.findById(orderNo);
                    if (order == null) {
                        throw new IllegalArgumentException("missing order");
                    }
                    order.ensureVersion(version);
                    order.ensureShippable();
                }

                public void cancel(String orderNo, User currentUser) {
                    Order order = orderRepository.findById(orderNo);
                    order.cancel(currentUser);
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderController.java"), """
            package io.atworks.classify;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.PathVariable;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestParam;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class OrderController {
                private OrderService orderService;

                @PostMapping("/classify/orders/{orderNo}/ship")
                public void ship(@PathVariable String orderNo, @RequestParam long version) {
                    orderService.ship(orderNo, version);
                }

                @GetMapping("/classify/orders/{orderNo}/cancel")
                public void cancel(@PathVariable String orderNo) {
                    orderService.cancel(orderNo, new User());
                }
            }
        """);
        Files.writeString(packageDir.resolve("OrderExceptionHandler.java"), """
            package io.atworks.classify;

            import org.springframework.http.HttpStatus;
            import org.springframework.web.bind.annotation.ExceptionHandler;
            import org.springframework.web.bind.annotation.ResponseStatus;
            import org.springframework.web.bind.annotation.RestControllerAdvice;

            @RestControllerAdvice
            public class OrderExceptionHandler {
                @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
                @ResponseStatus(HttpStatus.BAD_REQUEST)
                public String handleRuntime(RuntimeException ex) {
                    return ex.getMessage();
                }
            }
        """);

        StaticScanResult scanResult = new SpringStaticScanService().scan(repositorySource);
        ValidationExtractionResult extractionResult = new ValidationExtractionService().extract(scanResult, repositorySource);
        ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractionResult, repositorySource);

        assertThat(graph.nodes()).anyMatch(node -> node.type() == GraphNodeType.BUSINESS_RULE && node.label().startsWith("EXISTENCE_CHECK | order == null"));
        assertThat(graph.nodes()).anyMatch(node -> node.type() == GraphNodeType.BUSINESS_RULE && node.label().startsWith("VERSION_CHECK | version != requestedVersion"));
        assertThat(graph.nodes()).anyMatch(node -> node.type() == GraphNodeType.BUSINESS_RULE && node.label().startsWith("STATE_CHECK | !\"PAYED\".equals(status)"));
        assertThat(graph.nodes()).anyMatch(node -> node.type() == GraphNodeType.BUSINESS_RULE && node.label().startsWith("PERMISSION_CHECK | !cancelPolicy.hasCancellationPermission(this, currentUser)"));
        assertThat(graph.edges()).anyMatch(edge -> edge.type() == GraphEdgeType.EVALUATES && edge.evidence().startsWith("EXISTENCE_CHECK | if statement"));
        assertThat(graph.edges()).anyMatch(edge -> edge.type() == GraphEdgeType.EVALUATES && edge.evidence().startsWith("VERSION_CHECK | if statement"));
        assertThat(graph.edges()).anyMatch(edge -> edge.type() == GraphEdgeType.EVALUATES && edge.evidence().startsWith("STATE_CHECK | if statement"));
        assertThat(graph.edges()).anyMatch(edge -> edge.type() == GraphEdgeType.EVALUATES && edge.evidence().startsWith("PERMISSION_CHECK | if statement"));
    }
}
