package io.atworks.specscan.analysis.fact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FactGraphBuildResultRunnerTest {

    @TempDir
    Path workspace;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 자바 소스 코드 String을 전달받아 DefaultFactCodeGraphBuilder를 실행하고
     * FactGraphBuildResult 결과를 반환하는 유틸리티 메서드
     */
    public static FactGraphBuildResult buildFactGraphFromSource(
            Path workspaceRoot,
            String relativeJavaFilePath,
            String sourceCode,
            String controllerClass,
            String controllerMethod
    ) throws IOException {

        // 1. 소스 파일 저장
        Path file = workspaceRoot.resolve(relativeJavaFilePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, sourceCode);

        // 2. Scan 및 Ingestion 메타데이터 구축
        SourceTrace trace = new SourceTrace(relativeJavaFilePath, 1, 1);
        ApiEndpoint endpoint = new ApiEndpoint(
                "POST",
                "/api/test",
                controllerClass,
                controllerMethod,
                List.of(),
                new ResponseBinding("Object", trace),
                trace
        );
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 1, List.of(), null);

        RepositorySource source = new RepositorySource(
                new RepositoryIdentity("test-repo", "owner", "repo", "path", "main"),
                new WorkspaceContext("exec-id", workspaceRoot.toString(), Instant.now(), "cache", false),
                List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 1, 1, 1, "DETECTED")),
                "Gradle",
                new JavaInventorySummary(1, 1, 1, true, 0),
                List.of(),
                List.of(),
                new SafetyPolicyHint(List.of(), List.of(), "1.0"),
                new IngestionMetadata(Instant.now(), Instant.now(), 1, "LOCAL", "main", 0)
        );

        // 3. DefaultFactCodeGraphBuilder 실행
        DefaultFactCodeGraphBuilder builder = new DefaultFactCodeGraphBuilder();
        return builder.build(scan, source, FactGraphTraversalBudget.defaults());
    }

    @Test
    void executeDefaultFactCodeGraphBuilderAndGetBuildResult() throws Exception {
        String code = """
            package demo.service;
            
            import org.springframework.util.StringUtils;
            
            public class StreamProcessService {
            
                public Dto processDevelopers(Dto dto) {
                    validate(dto.a(), dto.b());
                    return dto;
                }
            
                private void validate(int a, String b) {
                    if (a <= 0 || !StringUtils.hasText(b)) {
                        throw new InvalidException();
                    }
                }
            
                public record Dto(int a, String b) {
                }
            
                static class InvalidException extends RuntimeException {
                }
            }
            """;

        // 1. FactGraphBuildResult 생성 실행
        FactGraphBuildResult result = buildFactGraphFromSource(
                workspace,
                "src/main/java/demo/service/StreamProcessService.java",
                code,
                "demo.service.StreamProcessService",
                "processDevelopers"
        );

        // 2. FactGraphBuildResult 검증
        assertThat(result).isNotNull();
        assertThat(result.graphs()).isNotEmpty();

        // 3. FactGraphBuildResult 전체 JSON 출력
        String jsonResult = OBJECT_MAPPER.writeValueAsString(result);
        System.out.println("=================================================");
        System.out.println("    [FactGraphBuildResult JSON Result Output]    ");
        System.out.println("=================================================");
        System.out.println(jsonResult);
    }
}
