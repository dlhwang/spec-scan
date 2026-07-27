package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.application.RuleOutputService;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCandidate;
import io.atworks.specscan.analysis.domain.evaluation.BaselineDiagnostic;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.rule.*;
import io.atworks.specscan.analysis.support.rule.pack.InitialRulePacks;
import io.atworks.specscan.ingestion.domain.*;
import java.time.Instant;
import java.util.*;

public final class JavaBaselineAnalysisAdapter {
    public record RawCorpusObservation(String corpusId, String sourceDigest,
                                       List<RawEndpointObservation> endpoints,
                                       List<BaselineDiagnostic> diagnostics) {
        public RawCorpusObservation {
            endpoints = List.copyOf(endpoints); diagnostics = List.copyOf(diagnostics);
        }
    }
    public record RawEndpointObservation(ApiEndpoint endpoint, FactCodeGraph graph,
                                         List<BusinessRuleCandidate> candidates,
                                         EndpointRuleOutput output,
                                         List<BaselineDiagnostic> diagnostics) {
        public RawEndpointObservation {
            candidates = List.copyOf(candidates); diagnostics = List.copyOf(diagnostics);
        }
    }

    public RawCorpusObservation analyze(String corpusId,
                                        EvaluationWorkspaceMaterializer.PreparedEvaluationWorkspace workspace,
                                        RunDeadline deadline, RunMetricsCollector metrics) {
        deadline.checkpoint(corpusId + ":scan");
        RepositorySource source = source(corpusId, workspace);
        StaticScanResult scan = new SpringStaticScanService().scan(source);
        deadline.checkpoint(corpusId + ":graph");
        FactGraphBuildResult build = new DefaultFactCodeGraphBuilder().build(scan, source,
            FactGraphTraversalBudget.defaults());
        Map<String, EndpointRuleOutput> outputs = new RuleOutputService().generate(scan, build, List.of());
        GraphRuleEngine engine = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all());
        List<RawEndpointObservation> endpointResults = new ArrayList<>();
        Map<String, FactCodeGraph> graphs = new HashMap<>();
        for (FactCodeGraph graph : build.graphs()) {
            ApiEndpoint endpoint = endpointFor(scan.endpoints(), graph);
            if (endpoint != null) graphs.put(OperationKey.of(endpoint).externalKey(), graph);
        }
        for (ApiEndpoint endpoint : scan.endpoints().stream()
                .sorted(Comparator.comparing(ep -> OperationKey.of(ep).externalKey())).toList()) {
            deadline.checkpoint(corpusId + ":" + endpoint.controllerMethod());
            String key = OperationKey.of(endpoint).externalKey();
            FactCodeGraph graph = graphs.get(key);
            List<BusinessRuleCandidate> candidates = List.of();
            List<BaselineDiagnostic> diagnostics = new ArrayList<>();
            if (graph != null) {
                GraphRuleEngineResult evaluated = engine.evaluate(graph, methodScope(graph));
                candidates = evaluated.candidates().businessRules();
                metrics.addGraph(graph.nodes().size(), graph.edges().size(), candidates.size());
                evaluated.report().diagnostics().forEach(diagnostic -> diagnostics.add(new BaselineDiagnostic(
                    diagnostic.code(), BaselineDiagnostic.Severity.WARNING, "rule", corpusId, key,
                    diagnostic.ruleId(), diagnostic.message())));
            } else {
                diagnostics.add(new BaselineDiagnostic("FACT_GRAPH_UNAVAILABLE", BaselineDiagnostic.Severity.ERROR,
                    "graph", corpusId, key, null, "endpoint has no fact graph"));
            }
            metrics.checkpoint("endpoint", corpusId, key);
            endpointResults.add(new RawEndpointObservation(endpoint, graph, candidates,
                outputs.getOrDefault(key, EndpointRuleOutput.empty(endpoint.path())), diagnostics));
        }
        List<BaselineDiagnostic> corpusDiagnostics = build.diagnostics().stream().map(diagnostic ->
            new BaselineDiagnostic("FACT_GRAPH_" + diagnostic.reason(),
                diagnostic.truncated() ? BaselineDiagnostic.Severity.ERROR : BaselineDiagnostic.Severity.WARNING,
                "graph", corpusId, null, diagnostic.apiMethod(), diagnostic.details())).toList();
        return new RawCorpusObservation(corpusId, workspace.sourceDigest(), endpointResults, corpusDiagnostics);
    }

    private MethodScope methodScope(FactCodeGraph graph) {
        Map<String, FactNode> nodes = new HashMap<>(); graph.nodes().forEach(node -> nodes.put(node.id(), node));
        Set<String> methods = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(); pending.add(graph.apiMethodNodeId());
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            FactNode node = nodes.get(current);
            if (node != null && (node.type() == FactNodeType.API_METHOD || node.type() == FactNodeType.METHOD
                    || node.type() == FactNodeType.CONSTRUCTOR)) methods.add(current);
            for (FactEdge edge : graph.edges()) {
                String next = edge.sourceNodeId().equals(current) ? edge.targetNodeId()
                    : edge.targetNodeId().equals(current) ? edge.sourceNodeId() : null;
                if (next != null) {
                    FactNode neighbor = nodes.get(next);
                    if (neighbor != null && neighbor.type() != FactNodeType.API_METHOD) pending.addLast(next);
                }
            }
        }
        return new MethodScope(graph.graphId(), methods);
    }

    private ApiEndpoint endpointFor(List<ApiEndpoint> endpoints, FactCodeGraph graph) {
        FactNode root = graph.nodes().stream().filter(node -> node.id().equals(graph.apiMethodNodeId()))
            .findFirst().orElse(null);
        if (root == null || !(root.payload() instanceof FactNodePayload.MethodPayload method)) return null;
        return endpoints.stream().filter(endpoint -> endpoint.controllerClass().equals(method.owner())
            && method.declarationSignature().startsWith(endpoint.controllerMethod() + "(")).findFirst().orElse(null);
    }

    private RepositorySource source(String corpusId,
                                    EvaluationWorkspaceMaterializer.PreparedEvaluationWorkspace workspace) {
        Instant now = Instant.now();
        List<SourceRootCandidate> roots = workspace.sourceRoots().stream().map(root ->
            new SourceRootCandidate(root, root, "Gradle", true, 1, 1, 1, "DETECTED")).toList();
        return new RepositorySource(new RepositoryIdentity("u01", "fixture", corpusId, corpusId, "pinned"),
            new WorkspaceContext("u01-" + corpusId, workspace.root().toString(), now, "local", false),
            roots, "Gradle", new JavaInventorySummary(1, 1, 1, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 0, "LOCAL", "pinned", 0));
    }
}
