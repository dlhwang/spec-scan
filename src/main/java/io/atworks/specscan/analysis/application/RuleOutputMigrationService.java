package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.output.*;
import io.atworks.specscan.analysis.support.rule.*;
import io.atworks.specscan.analysis.support.rule.pack.InitialRulePacks;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import java.util.*;

public final class RuleOutputMigrationService {
    private final CandidateToOutputAdapter adapter = new CandidateToOutputAdapter();
    private final CandidateOutputComparator comparator = new CandidateOutputComparator();
    private final ResponseMetadataAdapter responseMetadataAdapter = new ResponseMetadataAdapter();
    private final RequestBindingConditionAdapter requestBindingAdapter = new RequestBindingConditionAdapter();
    private final StructuralConditionAdapter structuralConditionAdapter = new StructuralConditionAdapter();
    private final ResponseInvariantAdapter responseInvariantAdapter = new ResponseInvariantAdapter();

    public RuleOutputMigrationResult migrate(StaticScanResult scan, RepositorySource source,
                                             List<ApiCondition> legacyConditions) {
        Map<String, EndpointRuleOutput> outputs = new LinkedHashMap<>();
        scan.endpoints().forEach(endpoint -> outputs.put(OperationKey.of(endpoint).externalKey(),
            EndpointRuleOutput.empty(endpoint.path())));
        FactGraphBuildResult build = new DefaultFactCodeGraphBuilder().build(scan, source, FactGraphTraversalBudget.defaults());
        GraphRuleEngine engine = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all());
        for (FactCodeGraph graph : build.graphs()) {
            ApiEndpoint endpoint = endpointFor(scan.endpoints(), graph);
            if (endpoint == null) continue;
            Set<String> methodIds = new LinkedHashSet<>();
            for (FactNode node : graph.nodes()) if (node.type() == FactNodeType.API_METHOD
                    || node.type() == FactNodeType.METHOD || node.type() == FactNodeType.CONSTRUCTOR)
                methodIds.add(node.id());
            GraphRuleEngineResult evaluated = engine.evaluate(graph, new MethodScope(graph.graphId(), methodIds));
            EndpointRuleOutput candidateOutput = adapter.adapt(endpoint, evaluated.candidates().businessRules());
            EndpointRuleOutput requestOutput = requestBindingAdapter.augment(endpoint, candidateOutput);
            requestOutput = structuralConditionAdapter.augment(endpoint, requestOutput, legacyConditions);
            EndpointRuleOutput responseOutput = responseMetadataAdapter.augment(endpoint, requestOutput, graph);
            outputs.put(OperationKey.of(endpoint).externalKey(), responseInvariantAdapter.augment(endpoint,
                responseOutput, graph, evaluated.candidates().businessRules()));
        }
        List<CandidateOutputComparisonReport> reports = scan.endpoints().stream()
            .map(endpoint -> comparator.compare(endpoint, legacyConditions,
                outputs.get(OperationKey.of(endpoint).externalKey())))
            .toList();
        return new RuleOutputMigrationResult(outputs, reports);
    }

    private ApiEndpoint endpointFor(List<ApiEndpoint> endpoints, FactCodeGraph graph) {
        FactNode root = graph.nodes().stream().filter(node -> node.id().equals(graph.apiMethodNodeId()))
            .findFirst().orElse(null);
        if (root == null || !(root.payload() instanceof FactNodePayload.MethodPayload method)) return null;
        return endpoints.stream().filter(endpoint -> endpoint.controllerClass().equals(method.owner())
            && method.declarationSignature().startsWith(endpoint.controllerMethod() + "(")).findFirst().orElse(null);
    }
}
