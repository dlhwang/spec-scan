package io.atworks.specscan.analysis;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.support.TypeResolver;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpringStaticScanServiceTest {

    private SpringStaticScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new SpringStaticScanService();
    }

    @Test
    void testSpringStaticScanAndTypeResolution(@TempDir Path tempDir) throws IOException {
        // Given
        Path srcRoot = tempDir.resolve("src/main/java");
        Files.createDirectories(srcRoot);

        // 1. DTO 클래스 파일 생성
        Path dtoDir = srcRoot.resolve("io/atworks/dto");
        Files.createDirectories(dtoDir);
        Path dtoFile = dtoDir.resolve("UserDto.java");
        Files.writeString(dtoFile, """
            package io.atworks.dto;
            public class UserDto {
                private String username;
                private int age;
            }
        """);

        // 2. 컨트롤러 클래스 파일 생성
        Path controllerDir = srcRoot.resolve("io/atworks/controller");
        Files.createDirectories(controllerDir);
        Path controllerFile = controllerDir.resolve("TestUserController.java");
        Files.writeString(controllerFile, """
            package io.atworks.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            import io.atworks.dto.UserDto;
            
            @RestController
            @RequestMapping("/api/v1/users")
            public class TestUserController {
            
                @GetMapping("/{id}")
                public ResponseEntity<UserDto> getUser(
                        @RequestHeader("X-Auth-Token") String token,
                        @PathVariable("id") Long userId,
                        @RequestParam(value = "search", required = false) String searchKeyword) {
                    return null;
                }
                
                @PostMapping
                public ResponseEntity<Void> createUser(@RequestBody UserDto userDto) {
                    return null;
                }
                
                @PutMapping("/update")
                public void updateUser(UserDto userDto) {
                }
            }
        """);

        // 3. 비정상 (문법 오류) 파일 생성 - 결함 격리 테스트용
        Path brokenFile = controllerDir.resolve("BrokenController.java");
        Files.writeString(brokenFile, """
            package io.atworks.controller;
            @RestController
            public class BrokenController {
                // 문법 에러 유발
                public void wrongMethod( {
                }
            }
        """);

        // RepositorySource 셋업
        RepositoryIdentity identity = new RepositoryIdentity("github.com", "owner", "repo", "https://github.com/owner/repo.git", "main");
        WorkspaceContext workspace = new WorkspaceContext("exec-123", tempDir.toAbsolutePath().toString(), Instant.now(), "cache-key", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, 3, 3, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(3, 1, 1, true, 0);
        IngestionMetadata ingestionMetadata = new IngestionMetadata(Instant.now(), Instant.now(), 10, "BRANCH", "main", 0);

        RepositorySource repositorySource = new RepositorySource(
            identity,
            workspace,
            sourceRoots,
            "Gradle",
            javaSummary,
            List.of(),
            List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            ingestionMetadata
        );

        // When
        StaticScanResult result = scanService.scan(repositorySource);

        // Then
        // 1. 파싱 오류(BrokenController)에 따른 warning 검증
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).warningCode()).isEqualTo("PARSING_FAILED");

        // 2. 스캔 성공 엔드포인트 개수 검증
        List<ApiEndpoint> endpoints = result.endpoints();
        assertThat(endpoints).hasSize(3);

        // 3. GET /api/v1/users/{id} 엔드포인트 세부 검증
        ApiEndpoint getEndpoint = endpoints.stream()
                .filter(e -> e.httpMethod().equals("GET"))
                .findFirst()
                .orElseThrow();
        assertThat(getEndpoint.path()).isEqualTo("/api/v1/users/{id}");
        assertThat(getEndpoint.controllerMethod()).isEqualTo("getUser");
        assertThat(getEndpoint.responseBinding().type()).isEqualTo("UserDto");
        assertThat(getEndpoint.sourceTrace().fileRelativePath()).endsWith("TestUserController.java");
        assertThat(getEndpoint.sourceTrace().startLine()).isGreaterThan(0);

        // GET 파라미터 바인딩 검증
        List<RequestBinding> getBindings = getEndpoint.requestBindings();
        assertThat(getBindings).hasSize(3);

        RequestBinding headerBinding = getBindings.stream().filter(b -> b.parameterName().equals("token")).findFirst().orElseThrow();
        assertThat(headerBinding.targetLocation()).isEqualTo(BindingLocation.HEADER);
        assertThat(headerBinding.isRequired()).isTrue();
        assertThat(headerBinding.type()).isEqualTo("String");

        RequestBinding pathBinding = getBindings.stream().filter(b -> b.parameterName().equals("userId")).findFirst().orElseThrow();
        assertThat(pathBinding.targetLocation()).isEqualTo(BindingLocation.PATH);
        assertThat(pathBinding.isRequired()).isTrue();
        assertThat(pathBinding.type()).isEqualTo("Long");

        RequestBinding queryBinding = getBindings.stream().filter(b -> b.parameterName().equals("searchKeyword")).findFirst().orElseThrow();
        assertThat(queryBinding.targetLocation()).isEqualTo(BindingLocation.QUERY);
        assertThat(queryBinding.isRequired()).isFalse();

        // 4. POST /api/v1/users 엔드포인트 세부 검증
        ApiEndpoint postEndpoint = endpoints.stream()
                .filter(e -> e.httpMethod().equals("POST"))
                .findFirst()
                .orElseThrow();
        assertThat(postEndpoint.path()).isEqualTo("/api/v1/users");
        assertThat(postEndpoint.responseBinding().type()).isEqualTo("Void");
        
        List<RequestBinding> postBindings = postEndpoint.requestBindings();
        assertThat(postBindings).hasSize(1);
        assertThat(postBindings.get(0).targetLocation()).isEqualTo(BindingLocation.BODY);
        assertThat(postBindings.get(0).type()).isEqualTo("UserDto");

        // 5. PUT /api/v1/users/update 엔드포인트 세부 검증 (어노테이션 없는 사용자 정의 POJO DTO -> QUERY 분류 검증)
        ApiEndpoint putEndpoint = endpoints.stream()
                .filter(e -> e.httpMethod().equals("PUT"))
                .findFirst()
                .orElseThrow();
        assertThat(putEndpoint.path()).isEqualTo("/api/v1/users/update");
        List<RequestBinding> putBindings = putEndpoint.requestBindings();
        assertThat(putBindings).hasSize(1);
        assertThat(putBindings.get(0).targetLocation()).isEqualTo(BindingLocation.QUERY);
        assertThat(putBindings.get(0).type()).isEqualTo("UserDto");

        // 6. TypeResolver를 통한 DTO 구조 해석 검증
        TypeResolver typeResolver = new TypeResolver(List.of(srcRoot));
        Optional<ClassOrInterfaceDeclaration> dtoDeclOpt = typeResolver.resolveClassDeclaration("UserDto");
        assertThat(dtoDeclOpt).isPresent();
        ClassOrInterfaceDeclaration dtoDecl = dtoDeclOpt.get();
        assertThat(dtoDecl.getNameAsString()).isEqualTo("UserDto");
        assertThat(dtoDecl.getFields()).hasSize(2);
    }
}
