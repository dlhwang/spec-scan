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
                .anyMatch(node -> node.type() == GraphNodeType.SERVICE_METHOD && node.id().equals("SERVICE_METHOD:UserService.register"));
        assertThat(hasServiceMethod).isTrue();

        // 6. Business Rule Node (if-throw condition in Service) should exist
        boolean hasBusinessRule = graph.nodes().stream()
                .anyMatch(node -> node.type() == GraphNodeType.BUSINESS_RULE && node.id().startsWith("BUSINESS_RULE:UserService.register:"));
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
                .anyMatch(edge -> edge.sourceId().startsWith("ENDPOINT:POST:/users") && edge.targetId().equals("SERVICE_METHOD:UserService.register") && edge.type() == GraphEdgeType.CALLS);
        assertThat(endpointToService).isTrue();

        // Service evaluates Business Rule
        boolean serviceToRule = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().equals("SERVICE_METHOD:UserService.register") && edge.targetId().startsWith("BUSINESS_RULE:UserService.register:") && edge.type() == GraphEdgeType.EVALUATES);
        assertThat(serviceToRule).isTrue();

        // Business Rule throws Exception
        boolean ruleToException = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().startsWith("BUSINESS_RULE:UserService.register:") && edge.targetId().equals("EXCEPTION:IllegalArgumentException") && edge.type() == GraphEdgeType.THROWS);
        assertThat(ruleToException).isTrue();

        // Exception maps to HTTP Status
        boolean exceptionToStatus = graph.edges().stream()
                .anyMatch(edge -> edge.sourceId().equals("EXCEPTION:IllegalArgumentException") && edge.targetId().equals("HTTP_STATUS:400 BAD_REQUEST") && edge.type() == GraphEdgeType.MAPS_TO);
        assertThat(exceptionToStatus).isTrue();
    }
}
