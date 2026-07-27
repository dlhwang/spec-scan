package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.PipelineVisualizationArtifactExporter;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ExportVisualizationTest {

    @Test
    void exportVisualizationHtmlAndJson() throws Exception {
        Path outputDir = Path.of("build");
        Path workspace = Path.of("d:/workspace/auto-oas");
        
        // 1. 소스 파일 작성
        String code = """
            package demo.service;
            import java.util.List;
            import java.util.stream.Collectors;
            public class StreamProcessService {
                public List<String> processDevelopers(List<Developer> developers) {
                    return developers.stream()
                        .filter(dev -> dev.getDepartment().equals("개발팀"))
                        .filter(dev -> dev.getSalary() >= 5000)
                        .map(Developer::getName)
                        .collect(Collectors.toList());
                }
            }
            class Developer {
                private String name;
                private String department;
                private double salary;
                public String getName() { return name; }
                public String getDepartment() { return department; }
                public double getSalary() { return salary; }
            }
            """;

        Path file = workspace.resolve("src/main/java/demo/service/StreamProcessService.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, code);

        // 2. Scan 및 Ingestion 메타데이터 구축
        SourceTrace trace = new SourceTrace("src/main/java/demo/service/StreamProcessService.java", 1, 1);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/api/developers", "demo.service.StreamProcessService", "processDevelopers", List.of(), new ResponseBinding("List<String>", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 1, List.of(), null);

        RepositorySource source = new RepositorySource(
            new RepositoryIdentity("auto-oas", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec-id", workspace.toString(), Instant.now(), "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 1, 1, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(1, 1, 1, true, 0),
            List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(Instant.now(), Instant.now(), 1, "LOCAL", "main", 0)
        );

        // 3. DefaultFactCodeGraphBuilder 실행
        FactGraphBuildResult graphBuild = new DefaultFactCodeGraphBuilder().build(scan, source, FactGraphTraversalBudget.defaults());

        // 4. PipelineVisualizationArtifactExporter 내보내기 실행
        ValidationExtractionResult validation = new ValidationExtractionResult(List.of(), List.of(), List.of());
        Map<String, EndpointRuleOutput> ruleOutputs = Map.of();
        String executionJson = "{\"operations\": []}";

        PipelineVisualizationArtifactExporter exporter = new PipelineVisualizationArtifactExporter();
        exporter.export(outputDir, scan, validation, graphBuild, ruleOutputs, source, executionJson);

        System.out.println("Visualization generated at: " + outputDir.resolve("visualization/index.html").toAbsolutePath());
    }
}
