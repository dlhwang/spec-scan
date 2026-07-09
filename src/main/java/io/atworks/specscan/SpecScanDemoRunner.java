package io.atworks.specscan;

import io.atworks.specscan.analysis.application.*;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.ingestion.adapter.GitRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.adapter.TempWorkspacePreparerAdapter;
import io.atworks.specscan.ingestion.application.RepositoryIngestionService;
import io.atworks.specscan.ingestion.domain.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SpecScanDemoRunner {

    public static void main(String[] args) {
        com.github.javaparser.StaticJavaParser.getConfiguration()
            .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

        System.out.println("=================================================");
        System.out.println("   Spec Scan E2E Pipeline Demo Runner Start      ");
        System.out.println("=================================================");

        RepositoryIngestionService ingestionService = new RepositoryIngestionService(
            new GitRepositoryFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        Path dummyTempDir = null;
        RepositorySource repositorySource = null;

        try {
            if (args.length > 0) {
                // 1. 실제 Git URL로부터 Ingestion 수행
                String gitUrl = args[0];
                System.out.println("[Step 1] Ingesting from real Git URL: " + gitUrl);
                RepositoryRequest request = new RepositoryRequest(gitUrl, null, null, null);
                repositorySource = ingestionService.ingest(request);
                
                System.out.println("[Ingestion Summary]");
                System.out.println("  - Detected Source Roots: " + repositorySource.sourceRoots().size());
                System.out.println("  - Java File Count: " + repositorySource.javaInventorySummary().totalJavaFileCount());
                System.out.println("  - Build Tool Hint: " + repositorySource.buildToolHint());

            } else {
                // 1. 로컬 임시 작업용 데모 소스 코드 생성
                dummyTempDir = Files.createTempDirectory("spec-scan-demo");
                Path srcRoot = dummyTempDir.resolve("src/main/java");
                Files.createDirectories(srcRoot);

                System.out.println("[Step 1] Preparing dummy Spring project source files at: " + dummyTempDir);

                // DTO
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

                // Custom Annotation
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

                // ConstraintValidator
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

                // Service
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

                // Controller
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

                // Exception Handler (ControllerAdvice)
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

                // RepositorySource 생성
                RepositoryIdentity identity = new RepositoryIdentity("local", "owner", "demo-repo", "local-path", "main");
                WorkspaceContext workspace = new WorkspaceContext("demo-exec", dummyTempDir.toAbsolutePath().toString(), java.time.Instant.now(), "demo-cache", false);
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

            // 2. Static Scanning
            System.out.println("[Step 2] Running Spring AST Static Scanning...");
            SpringStaticScanService scanService = new SpringStaticScanService();
            StaticScanResult scanResult = scanService.scan(repositorySource);
            System.out.println("  - Resolved Endpoints: " + scanResult.endpoints().size());

            // 3. Validation Extraction
            System.out.println("[Step 3] Extracting Validation Candidates (Annotations, Validators, Service Hints)...");
            ValidationExtractionService extractionService = new ValidationExtractionService();
            ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);
            System.out.println("  - Extracted Direct Conditions: " + extractionResult.directConditions().size());
            System.out.println("  - Extracted Candidates: " + extractionResult.candidates().size());

            // 4. OpenAPI Assembly & Output
            System.out.println("[Step 4] Normalizing Candidates & Assembling OpenAPI 3.0 Document...");
            OpenApiAssemblyService assemblyService = new OpenApiAssemblyService();
            Path outputPath = Paths.get("build/openapi.yaml");
            assemblyService.assemble(scanResult, extractionResult, repositorySource, outputPath);

            System.out.println("\n=================================================");
            System.out.println("🎉 SUCCESS: OpenAPI Document assembled successfully!");
            System.out.println("Target File Path: " + outputPath.toAbsolutePath());
            System.out.println("=================================================\n");

            // 최종 생성된 YAML 콘솔에 출력
            String yaml = Files.readString(outputPath);
            System.out.println(yaml);

            // 최종 생성된 Validation Evidence Graph 출력
            Path graphPath = outputPath.getParent().resolve("validation-evidence-graph.json");
            if (Files.exists(graphPath)) {
                System.out.println("\n=================================================");
                System.out.println("📊 Validation Evidence Graph JSON Output:");
                System.out.println("=================================================\n");
                String graphJson = Files.readString(graphPath);
                System.out.println(graphJson);
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: Demo Execution Failed!");
            e.printStackTrace();
        } finally {
            // 임시 파일들 정리
            if (dummyTempDir != null && Files.exists(dummyTempDir)) {
                System.out.println("[CleanUp] Cleaning up local dummy source files...");
                try {
                    Files.walk(dummyTempDir)
                         .sorted(java.util.Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(java.io.File::delete);
                } catch (IOException ignored) {}
            }
            if (args.length > 0 && repositorySource != null) {
                System.out.println("[CleanUp] Cleaning up git cloned workspace...");
                new TempWorkspacePreparerAdapter().clean(repositorySource.workspaceContext());
            }
        }
    }
}
