package io.atworks.apiintelligence.application;

import io.atworks.apiintelligence.adapter.file.JsonRunArtifactAdapter;
import io.atworks.apiintelligence.adapter.fact.FactGraphEvidenceAdapter;
import io.atworks.apiintelligence.adapter.javaparser.JavaParserApiDiscoveryAdapter;
import io.atworks.apiintelligence.adapter.javaparser.JavaParserCodeGraphAdapter;
import io.atworks.apiintelligence.adapter.javaparser.SourceEvidenceAdapter;
import io.atworks.apiintelligence.adapter.openai.OpenAiIntelligenceAdapter;
import io.atworks.apiintelligence.adapter.source.DefaultSourceWorkspaceAdapter;
import io.atworks.apiintelligence.config.ApiIntelligenceConfiguration;
import io.atworks.apiintelligence.domain.source.AnalysisSource;
import io.atworks.apiintelligence.domain.source.GitSource;
import io.atworks.apiintelligence.domain.source.LocalSource;
import io.atworks.apiintelligence.domain.source.RevisionType;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import io.atworks.specscan.analysis.application.ScanAnalysisContext;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactNodePayload;
import io.atworks.specscan.analysis.domain.output.OperationKey;

public final class ApiIntelligenceAnalysisService {

    public Map<String, Object> analyze(ScanAnalysisContext context,
        ApiIntelligenceConfiguration config) throws java.io.IOException {
        var model = new OpenAiIntelligenceAdapter(config.openai().endpoint(),
            config.openai().apiKey(), config.openai().model(), config.openai().maxInputCharacters());
        var evidenceAdapter = new FactGraphEvidenceAdapter();
        var graphs = new LinkedHashMap<String, Object>();
        var evidence = new LinkedHashMap<String, Object>();
        var results = new LinkedHashMap<String, Object>();
        var apis = new ArrayList<Map<String, Object>>();
        var artifacts = new JsonRunArtifactAdapter();
        var run = artifacts.createRun(config.outputRoot(), "run-" + System.currentTimeMillis());
        Path workspace = Path.of(context.repositorySource().workspaceContext().workspacePath());

        for (FactCodeGraph graph : context.factGraphs().graphs()) {
            ApiEndpoint endpoint = endpointFor(context, graph);
            if (endpoint == null) continue;
            String apiId = OperationKey.of(endpoint).externalKey();
            var graphEvidence = evidenceAdapter.collect(apiId, graph, workspace);
            var intelligence = model.analyze(apiId, graph, graphEvidence);
            graphs.put(apiId, graph);
            evidence.put(apiId, graphEvidence);
            results.put(apiId, intelligence);
            apis.add(Map.of(
                "apiId", apiId,
                "httpMethod", endpoint.httpMethod(),
                "path", endpoint.path(),
                "controllerType", endpoint.controllerClass(),
                "responseType", endpoint.responseBinding() == null
                    ? "Object" : endpoint.responseBinding().type()));
            artifacts.writeJson(run, "api/" + safeArtifactName(apiId) + "/graph.json", graph);
            artifacts.writeJson(run, "api/" + safeArtifactName(apiId) + "/evidence.json",
                graphEvidence);
            artifacts.writeJson(run, "api/" + safeArtifactName(apiId) + "/intelligence.json",
                intelligence);
        }
        return Map.of("runDirectory", run.toString(), "apis", apis, "graphs", graphs,
            "evidence", evidence, "intelligence", results);
    }

    private ApiEndpoint endpointFor(ScanAnalysisContext context, FactCodeGraph graph) {
        var root = graph.nodes().stream()
            .filter(node -> node.id().equals(graph.apiMethodNodeId())).findFirst().orElse(null);
        if (root == null || !(root.payload() instanceof FactNodePayload.MethodPayload method)) {
            return null;
        }
        return context.scanResult().endpoints().stream()
            .filter(endpoint -> endpoint.controllerClass().equals(method.owner())
                && method.declarationSignature().startsWith(endpoint.controllerMethod() + "("))
            .findFirst().orElse(null);
    }

    private String safeArtifactName(String apiId) {
        return apiId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public Map<String, Object> analyzeRepository(String repositorySource, String revisionType,
        String revision, ApiIntelligenceConfiguration config) throws java.io.IOException {
        AnalysisSource source;
        if (repositorySource.startsWith("http://") || repositorySource.startsWith("https://")) {
            source = new GitSource(java.net.URI.create(repositorySource),
                revision == null || revision.isBlank() ? null
                    : RevisionType.valueOf(revisionType.toUpperCase()), revision);
        } else {
            source = new LocalSource(Path.of(repositorySource));
        }
        return analyze(source, config);
    }

    public Map<String, Object> analyze(Path project, ApiIntelligenceConfiguration config)
        throws java.io.IOException {
        var source = new DefaultSourceWorkspaceAdapter().acquire(new LocalSource(project));
        try (var ws = source) {
            var apis = new JavaParserApiDiscoveryAdapter().discover(ws);
            var graphs = new LinkedHashMap<String, Object>();
            var evidence = new LinkedHashMap<String, Object>();
            var model = new OpenAiIntelligenceAdapter(config.openai().endpoint(),
                config.openai().apiKey(), config.openai().model());
            var results = new LinkedHashMap<String, Object>();
            var artifacts = new JsonRunArtifactAdapter();
            var run = artifacts.createRun(config.outputRoot(), "run-" + System.currentTimeMillis());
            for (var api : apis) {
                var g = new JavaParserCodeGraphAdapter().build(api, ws, config.graph());
                var ev = new SourceEvidenceAdapter().collect(g, ws);
                graphs.put(api.apiId(), g);
                evidence.put(api.apiId(), ev);
                results.put(api.apiId(), model.analyze(api.apiId(), g, ev));
                artifacts.writeJson(run, "api/" + api.apiId() + "/graph.json", g);
                artifacts.writeJson(run, "api/" + api.apiId() + "/evidence.json", ev);
                artifacts.writeJson(run, "api/" + api.apiId() + "/intelligence.json",
                    results.get(api.apiId()));
            }
            return Map.of("runDirectory", run.toString(), "apis", apis, "graphs", graphs,
                "evidence", evidence, "intelligence", results);
        }
    }

    public Map<String, Object> analyze(AnalysisSource input, ApiIntelligenceConfiguration config)
        throws java.io.IOException {
        var source = new DefaultSourceWorkspaceAdapter().acquire(input);
        try (var ws = source) {
            var apis = new JavaParserApiDiscoveryAdapter().discover(ws);
            var graphs = new LinkedHashMap<String, Object>();
            var evidence = new LinkedHashMap<String, Object>();
            var model = new OpenAiIntelligenceAdapter(config.openai().endpoint(),
                config.openai().apiKey(), config.openai().model());
            var results = new LinkedHashMap<String, Object>();
            var artifacts = new JsonRunArtifactAdapter();
            var run = artifacts.createRun(config.outputRoot(), "run-" + System.currentTimeMillis());
            for (var api : apis) {
                var g = new JavaParserCodeGraphAdapter().build(api, ws, config.graph());
                var ev = new SourceEvidenceAdapter().collect(g, ws);
                graphs.put(api.apiId(), g);
                evidence.put(api.apiId(), ev);
                results.put(api.apiId(), model.analyze(api.apiId(), g, ev));
                artifacts.writeJson(run, "api/" + api.apiId() + "/graph.json", g);
                artifacts.writeJson(run, "api/" + api.apiId() + "/evidence.json", ev);
                artifacts.writeJson(run, "api/" + api.apiId() + "/intelligence.json",
                    results.get(api.apiId()));
            }
            return Map.of("runDirectory", run.toString(), "apis", apis, "graphs", graphs,
                "evidence", evidence, "intelligence", results);
        }
    }
}
