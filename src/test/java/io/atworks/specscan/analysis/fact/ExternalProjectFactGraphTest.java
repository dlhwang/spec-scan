package io.atworks.specscan.analysis.fact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.PipelineVisualizationArtifactExporter;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalProjectFactGraphTest {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void testBuildFactGraphFromExternalWorkspace() throws Exception {
        // 1. 외부 프로젝트 경로 지정 (D:\workspace\demo-api-project)
        Path externalWorkspace = Path.of("d:/workspace/demo-api-project");

        // 2. Scan 및 Ingestion 메타데이터 구축
        SourceTrace trace = new SourceTrace("src/main/java/demo/controller/SamplePatternController.java", 1, 1);
        RequestBinding b1 = new RequestBinding("userId", BindingLocation.QUERY, "Long", true, null, null, null, List.of(), trace);
        RequestBinding b2 = new RequestBinding("orderRequest", BindingLocation.BODY, "SampleOrderRequest", true, null, null, null, List.of(), trace);

        ApiEndpoint endpoint = new ApiEndpoint(
                "POST", "/api/sample/orders",
                "demo.controller.SamplePatternController", "processOrder",
                List.of(b1, b2), new ResponseBinding("List<String>", trace), trace
        );
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 3, List.of(), null);

        RepositorySource source = new RepositorySource(
            new RepositoryIdentity("demo-api-project", "owner", "demo-api-project", "path", "main"),
            new WorkspaceContext("exec-id", externalWorkspace.toString(), Instant.now(), "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 3, 3, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(3, 1, 1, true, 0),
            List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(Instant.now(), Instant.now(), 3, "LOCAL", "main", 0)
        );

        // 3. DefaultFactCodeGraphBuilder 실행 (외부 프로젝트 파싱)
        DefaultFactCodeGraphBuilder builder = new DefaultFactCodeGraphBuilder();
        FactGraphBuildResult result = builder.build(scan, source, FactGraphTraversalBudget.defaults());

        // 4. 검증
        assertThat(result.graphs()).isNotEmpty();
        FactCodeGraph graph = result.graphs().get(0);

        boolean hasBinaryIfThrow = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.CONDITION && node.snippet().contains("orderRequest.getQuantity() <= 0"));
        boolean hasBooleanCallIfThrow = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.CONDITION && node.snippet().contains("!userService.isUserActive(userId)"));
        boolean hasValidationAnnotations = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.ANNOTATION);
        boolean hasLambdaStreamFilter = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.LAMBDA && node.snippet().contains("item -> item.getAmount() >= 1000"));

        System.out.println("=================================================");
        System.out.println("  [External Project FactGraph Verification]");
        System.out.println("  Path: " + externalWorkspace.toAbsolutePath());
        System.out.println("=================================================");
        System.out.println("1. BINARY_EXPR -> IF -> THROW : " + hasBinaryIfThrow);
        System.out.println("2. BOOLEAN_CALL -> IF -> THROW: " + hasBooleanCallIfThrow);
        System.out.println("3. VALIDATION_ANNOTATION      : " + hasValidationAnnotations);
        System.out.println("4. LAMBDA -> STREAM_FILTER    : " + hasLambdaStreamFilter);

        assertThat(hasBinaryIfThrow).isTrue();
        assertThat(hasBooleanCallIfThrow).isTrue();
        assertThat(hasValidationAnnotations).isTrue();
        assertThat(hasLambdaStreamFilter).isTrue();

        // 5. 시각화(visualization) 폴더를 외부 프로젝트 경로(d:/workspace/demo-api-project)로 내보내기
        ValidationExtractionResult validation = new ValidationExtractionResult(List.of(), List.of(), List.of());
        Map<String, EndpointRuleOutput> ruleOutputs = Map.of();
        String executionJson = "{\"operations\": []}";

        PipelineVisualizationArtifactExporter exporter = new PipelineVisualizationArtifactExporter();
        exporter.export(externalWorkspace, scan, validation, result, ruleOutputs, source, executionJson);

        System.out.println("\nVisualization HTML exported to: " + externalWorkspace.resolve("visualization/index.html").toAbsolutePath());
    }
}
