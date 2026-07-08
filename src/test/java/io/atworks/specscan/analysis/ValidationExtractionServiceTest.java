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
        // Given
        Path srcRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(srcRoot);

        // 1. DTO 클래스 생성 (표준 어노테이션 및 커스텀 어노테이션 포함)
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

        // 2. 커스텀 어노테이션 생성
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

        // 3. ConstraintValidator 생성
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

        // 4. 서비스 클래스 생성 (if-throw 비즈니스 예외 포함)
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

        // 5. 컨트롤러 클래스 생성 (서비스 체인 호출 포함)
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

        // Mock StaticScanResult 셋업
        SourceTrace dummyTrace = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 15);
        RequestBinding bodyBinding = new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), dummyTrace);
        ResponseBinding responseBinding = new ResponseBinding("void", dummyTrace);
        
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(bodyBinding),
            responseBinding,
            dummyTrace
        );

        StaticScanResult scanResult = new StaticScanResult(
            List.of(endpoint),
            5,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 5, "BRANCH", "main", 0)
        );

        // RepositorySource 셋업
        RepositoryIdentity identity = new RepositoryIdentity("github.com", "owner", "repo", "https://github.com/owner/repo.git", "main");
        WorkspaceContext workspace = new WorkspaceContext("exec-123", tempDir.toAbsolutePath().toString(), Instant.now(), "cache-key", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, 5, 5, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(5, 1, 1, true, 0);
        RepositorySource repositorySource = new RepositorySource(
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

        // When
        ValidationExtractionResult result = extractionService.extract(scanResult, repositorySource);

        // Then
        // 1. Direct Conditions 검증 (@NotNull, @Size)
        List<ApiConditionDraft> drafts = result.directConditions();
        assertThat(drafts).hasSize(2);
        
        ApiConditionDraft notNullDraft = drafts.stream().filter(d -> d.operator().equals("NOT_NULL")).findFirst().orElseThrow();
        assertThat(notNullDraft.targetPath()).isEqualTo("username");

        ApiConditionDraft sizeDraft = drafts.stream().filter(d -> d.operator().equals("SIZE")).findFirst().orElseThrow();
        assertThat(sizeDraft.targetPath()).isEqualTo("nickname");
        assertThat(sizeDraft.expected()).contains("min=5").contains("max=20");

        // 2. Candidates 검증 (Custom annotation, Validator, Service Hint)
        List<ValidationCandidate> candidates = result.candidates();
        assertThat(candidates).hasSize(3);

        // 2-1. Custom Annotation 후보 검증
        ValidationCandidate annCand = candidates.stream().filter(c -> c.sourceType().equals("CUSTOM_ANNOTATION")).findFirst().orElseThrow();
        assertThat(annCand.targetPath()).isEqualTo("email");
        assertThat(annCand.evidenceSnippet()).contains("@ValidEmail");
        assertThat(annCand.confidence()).isEqualTo(1.0);

        // 2-2. Validator 후보 검증
        ValidationCandidate valCand = candidates.stream().filter(c -> c.sourceType().equals("VALIDATOR")).findFirst().orElseThrow();
        assertThat(valCand.targetPath()).isEqualTo("email");
        assertThat(valCand.evidenceSnippet()).contains("EmailValidator.isValid()");
        assertThat(valCand.confidence()).isEqualTo(1.0);

        // 2-3. Service Hint 후보 검증
        ValidationCandidate svcCand = candidates.stream().filter(c -> c.sourceType().equals("SERVICE_HINT")).findFirst().orElseThrow();
        assertThat(svcCand.targetPath()).isEqualTo("age"); // user.getAge() -> age로 유추됨
        assertThat(svcCand.evidenceSnippet()).contains("throw new IllegalArgumentException");
        assertThat(svcCand.confidence()).isEqualTo(0.5); // 간접 매핑 0.5
    }
}
