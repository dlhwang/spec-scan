package io.atworks.specscan.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
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
        // Given
        Path outputPath = tempDir.resolve("openapi.yaml");
        Path srcRoot = tempDir.resolve("src/main/java");
        Path dtoDir = srcRoot.resolve("io/atworks/dto");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("UserDto.java"), """
            package io.atworks.dto;
            public class UserDto {
                private String username;
                private String nickname;
                private String email;
                private int age;
            }
        """);

        // 1. Mock StaticScanResult
        SourceTrace dummyTrace = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 15);
        RequestBinding bodyBinding = new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), dummyTrace);
        ResponseBinding responseBinding = new ResponseBinding("UserDto", dummyTrace);
        
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
            1,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "BRANCH", "main", 0)
        );

        // 2. Mock ValidationExtractionResult
        ApiConditionDraft draft1 = new ApiConditionDraft("username", "NOT_NULL", "true", "@NotNull", dummyTrace);
        ApiConditionDraft draft2 = new ApiConditionDraft("nickname", "SIZE", "min=5, max=20", "@Size", dummyTrace);
        
        ValidationCandidate candidate1 = new ValidationCandidate("cand-1", "CUSTOM_ANNOTATION", "email", "@ValidEmail", 1.0, dummyTrace);
        ValidationCandidate candidate2 = new ValidationCandidate("cand-2", "SERVICE_HINT", "age", "user.getAge() < 19", 0.5, dummyTrace);
        
        // invalid candidate to test rejection
        ValidationCandidate invalidCandidate = new ValidationCandidate("cand-invalid", "VALIDATOR", "unknown", "", 1.0, dummyTrace);

        ValidationExtractionResult extractResult = new ValidationExtractionResult(
            List.of(draft1, draft2),
            List.of(candidate1, candidate2, invalidCandidate),
            List.of()
        );

        // 3. Mock RepositorySource
        RepositoryIdentity identity = new RepositoryIdentity("github.com", "owner", "repo", "https://github.com/owner/repo.git", "main");
        WorkspaceContext workspace = new WorkspaceContext("exec-123", tempDir.toAbsolutePath().toString(), Instant.now(), "cache-key", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, 1, 1, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(1, 1, 1, true, 0);
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
        assemblyService.assemble(scanResult, extractResult, repositorySource, outputPath);

        // Then
        // 1. File verification
        assertThat(outputPath).exists();
        Path structuredOutputPath = tempDir.resolve("api-spec-analysis.json");
        assertThat(structuredOutputPath).exists();
        Path executionOutputPath = tempDir.resolve("api-execution-model.json");
        assertThat(executionOutputPath).exists();
        String yamlContent = Files.readString(outputPath);
        JsonNode structuredJson = objectMapper.readTree(Files.readString(structuredOutputPath));
        JsonNode executionJson = objectMapper.readTree(Files.readString(executionOutputPath));

        // 2. Structural checks in YAML
        assertThat(yamlContent)
            .contains("openapi: 3.0.3")
            .contains("paths:")
            .contains("/users:")
            .contains("post:")
            .contains("components:")
            .contains("schemas:")
            .contains("UserDto:");

        // 3. Validation property injection checks
        assertThat(yamlContent)
            .contains("required:")
            .contains("- username")
            .contains("nickname:")
            .contains("minLength: 5")
            .contains("maxLength: 20")
            .contains("email:")
            .contains("format: email")
            .contains("age:")
            .contains("minimum: 19");

        assertThat(structuredJson.at("/apiVersions/0/method").asText()).isEqualTo("POST");
        assertThat(structuredJson.at("/apiVersions/0/endpoint").asText()).isEqualTo("/users");
        assertThat(structuredJson.at("/apiVersions/0/contentType").asText()).isEqualTo("JSON");
        assertThat(structuredJson.at("/apiVersions/0/jsonRequestBody").asText()).contains("username");
        assertThat(structuredJson.at("/valueValidations/0/jsonPath").asText()).isEqualTo("$.username");
        assertThat(structuredJson.at("/valueValidations/1/condition").asText()).isEqualTo("SIZE");
        assertThat(structuredJson.at("/valueValidations/3/condition").asText()).isEqualTo("MIN_AGE");

        assertThat(executionJson.at("/operations/0/request/bodySchema/type").asText()).isEqualTo("object");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/username/type").asText()).isEqualTo("string");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/nickname/minLength").asInt()).isEqualTo(5);
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/nickname/maxLength").asInt()).isEqualTo(20);
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/email/format").asText()).isEqualTo("email");
        assertThat(executionJson.at("/operations/0/request/bodySchema/properties/age/minimum").asInt()).isEqualTo(19);
        assertThat(executionJson.at("/operations/0/request/bodyExample/username").asText()).isEmpty();
        assertThat(executionJson.at("/operations/0/response200/schema/properties/age/type").asText()).isEqualTo("integer");
        assertThat(executionJson.at("/operations/0/validationConditions").isArray()).isTrue();
        assertThat(executionJson.at("/operations/0/validationConditions").size()).isGreaterThanOrEqualTo(4);
    }
}
