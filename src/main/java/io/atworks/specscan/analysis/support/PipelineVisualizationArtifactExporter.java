package io.atworks.specscan.analysis.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.FactNodePayload;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes the self-contained JSON data consumed by the static pipeline viewer. */
public final class PipelineVisualizationArtifactExporter {

    private static final int MAX_SOURCE_CHARACTERS = 200_000;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void export(Path outputDirectory, StaticScanResult scan, ValidationExtractionResult validation,
                       FactGraphBuildResult graphBuild, Map<String, EndpointRuleOutput> ruleOutputs,
                       RepositorySource repository, String executionJson) throws IOException {
        Path visualization = outputDirectory.resolve("visualization");
        Path tabs = visualization.resolve("tabs");
        Files.createDirectories(tabs);
        copyViewer(visualization.resolve("index.html"));

        List<Map<String, Object>> endpoints = endpointIndex(scan.endpoints());
        write(visualization.resolve("manifest.json"), Map.of(
            "schemaVersion", "1.0",
            "generatedAt", Instant.now().toString(),
            "repository", repositoryView(repository),
            "endpoints", endpoints,
            "tabs", Map.of(
                "source", "tabs/01-source.json",
                "pipeline", "tabs/02-pipeline.json",
                "graph", "tabs/03-graph.json",
                "candidates", "tabs/04-candidates.json",
                "rules", "tabs/05-rules.json",
                "finalSpec", "tabs/06-final-spec.json"
            )
        ));
        write(tabs.resolve("01-source.json"), sourceArtifact(scan, graphBuild, repository));
        write(tabs.resolve("02-pipeline.json"), pipelineArtifact(scan, validation, graphBuild, ruleOutputs));
        write(tabs.resolve("03-graph.json"), graphArtifact(scan, graphBuild));
        write(tabs.resolve("04-candidates.json"), candidatesArtifact(validation));
        write(tabs.resolve("05-rules.json"), rulesArtifact(ruleOutputs));
        JsonNode finalSpec = mapper.readTree(executionJson);
        write(tabs.resolve("06-final-spec.json"), finalSpec);
    }

    private Map<String, Object> repositoryView(RepositorySource repository) {
        var identity = repository.repositoryIdentity();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", identity.host());
        result.put("owner", identity.owner());
        result.put("name", identity.repositoryName());
        result.put("requestedRef", identity.requestedRef());
        result.put("buildTool", repository.buildToolHint());
        result.put("javaFiles", repository.javaInventorySummary().totalJavaFileCount());
        return result;
    }

    private List<Map<String, Object>> endpointIndex(List<ApiEndpoint> endpoints) {
        return endpoints.stream().map(endpoint -> Map.<String, Object>of(
            "operationKey", OperationKey.of(endpoint).externalKey(),
            "method", endpoint.httpMethod(),
            "path", endpoint.path(),
            "controllerClass", endpoint.controllerClass(),
            "controllerMethod", endpoint.controllerMethod()
        )).toList();
    }

    private Map<String, Object> sourceArtifact(StaticScanResult scan, FactGraphBuildResult graphBuild,
                                               RepositorySource repository) {
        List<Map<String, Object>> results = new ArrayList<>();
        Path workspace = Path.of(repository.workspaceContext().workspacePath()).toAbsolutePath().normalize();
        for (ApiEndpoint endpoint : scan.endpoints()) {
            FactCodeGraph graph = graphFor(endpoint, graphBuild.graphs());
            Set<String> paths = new LinkedHashSet<>();
            if (endpoint.sourceTrace() != null) paths.add(endpoint.sourceTrace().fileRelativePath());
            if (graph != null) graph.nodes().forEach(node -> paths.add(node.sourceRange().relativePath()));
            List<Map<String, Object>> files = new ArrayList<>();
            for (String relativePath : paths) {
                Path source = workspace.resolve(relativePath).normalize();
                if (!source.startsWith(workspace) || !Files.isRegularFile(source)) continue;
                try {
                    String content = Files.readString(source, StandardCharsets.UTF_8);
                    boolean truncated = content.length() > MAX_SOURCE_CHARACTERS;
                    if (truncated) content = content.substring(0, MAX_SOURCE_CHARACTERS);
                    files.add(Map.of("path", relativePath.replace('\\', '/'), "content", content,
                        "truncated", truncated, "highlights", highlights(relativePath, endpoint, graph)));
                } catch (IOException ignored) {
                    // A missing optional source file must not fail the primary scan output.
                }
            }
            results.add(Map.of("operationKey", OperationKey.of(endpoint).externalKey(), "files", files));
        }
        return Map.of("endpoints", results);
    }

    private List<Map<String, Object>> highlights(String path, ApiEndpoint endpoint, FactCodeGraph graph) {
        List<Map<String, Object>> highlights = new ArrayList<>();
        if (endpoint.sourceTrace() != null && path.equals(endpoint.sourceTrace().fileRelativePath())) {
            highlights.add(Map.of("id", "endpoint", "startLine", endpoint.sourceTrace().startLine(),
                "endLine", endpoint.sourceTrace().endLine(), "type", "API_ENDPOINT"));
        }
        if (graph != null) {
            graph.nodes().stream().filter(node -> path.equals(node.sourceRange().relativePath())).forEach(node ->
                highlights.add(Map.of("id", node.id(), "startLine", node.sourceRange().startLine(),
                    "endLine", node.sourceRange().endLine(), "type", node.type().name())));
        }
        return highlights;
    }

    private Map<String, Object> pipelineArtifact(StaticScanResult scan, ValidationExtractionResult validation,
                                                  FactGraphBuildResult graphs,
                                                  Map<String, EndpointRuleOutput> outputs) {
        return Map.of(
            "summary", Map.of("scannedClasses", scan.scannedClassesCount(),
                "endpoints", scan.endpoints().size(), "graphs", graphs.graphs().size(),
                "directConditions", validation.directConditions().size(),
                "validationCandidates", validation.candidates().size(), "outputs", outputs.size()),
            "steps", List.of(
                Map.of("phase", "ENDPOINT_SCAN", "component", "SpringStaticScanService", "count", scan.endpoints().size()),
                Map.of("phase", "FACT_GRAPH", "component", "DefaultFactCodeGraphBuilder", "count", graphs.graphs().size()),
                Map.of("phase", "CANDIDATE_EXTRACTION", "component", "ValidationExtractionService", "count", validation.candidates().size()),
                Map.of("phase", "OUTPUT_ASSEMBLY", "component", "RuleOutputService", "count", outputs.size())),
            "warnings", validation.warnings(), "graphDiagnostics", graphs.diagnostics());
    }

    private Map<String, Object> graphArtifact(StaticScanResult scan, FactGraphBuildResult graphBuild) {
        List<Map<String, Object>> graphs = new ArrayList<>();
        for (ApiEndpoint endpoint : scan.endpoints()) {
            FactCodeGraph graph = graphFor(endpoint, graphBuild.graphs());
            graphs.add(Map.of("operationKey", OperationKey.of(endpoint).externalKey(),
                "graph", graph == null ? Map.of("nodes", List.of(), "edges", List.of()) : graph));
        }
        return Map.of("endpoints", graphs, "diagnostics", graphBuild.diagnostics());
    }

    private Map<String, Object> candidatesArtifact(ValidationExtractionResult validation) {
        Map<String, List<Object>> byOperation = new LinkedHashMap<>();
        for (var candidate : validation.candidates()) {
            String key = candidate.operationKey() == null ? "unassigned" : candidate.operationKey();
            byOperation.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }
        return Map.of("directConditions", validation.directConditions(),
            "validationCandidatesByOperation", byOperation, "warnings", validation.warnings());
    }

    private Map<String, Object> rulesArtifact(Map<String, EndpointRuleOutput> outputs) {
        return Map.of("outputsByOperation", outputs);
    }

    private FactCodeGraph graphFor(ApiEndpoint endpoint, List<FactCodeGraph> graphs) {
        for (FactCodeGraph graph : graphs) {
            FactNode root = graph.nodes().stream()
                .filter(node -> node.id().equals(graph.apiMethodNodeId())).findFirst().orElse(null);
            if (root != null && root.payload() instanceof FactNodePayload.MethodPayload method
                && endpoint.controllerClass().equals(method.owner())
                && method.declarationSignature().startsWith(endpoint.controllerMethod() + "(")) return graph;
        }
        return null;
    }

    private void write(Path target, Object value) throws IOException {
        Files.writeString(target, mapper.writeValueAsString(value), StandardCharsets.UTF_8);
    }

    private void copyViewer(Path target) throws IOException {
        try (InputStream stream = PipelineVisualizationArtifactExporter.class
            .getResourceAsStream("/visualization/index.html")) {
            if (stream == null) throw new IOException("Missing visualization/index.html resource");
            Files.copy(stream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
