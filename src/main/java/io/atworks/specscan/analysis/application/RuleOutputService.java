package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.ConditionLocation;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.FactNodePayload;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import io.atworks.specscan.analysis.domain.rule.GraphRuleEngine;
import io.atworks.specscan.analysis.domain.rule.GraphRuleEngineResult;
import io.atworks.specscan.analysis.domain.rule.MethodScope;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.output.CandidateToOutputAdapter;
import io.atworks.specscan.analysis.support.output.RequestBindingConditionAdapter;
import io.atworks.specscan.analysis.support.output.ResponseMetadataAdapter;
import io.atworks.specscan.analysis.support.output.StructuralConditionAdapter;
import io.atworks.specscan.analysis.support.rule.DefaultGraphRuleEngine;
import io.atworks.specscan.analysis.support.rule.DefaultValidationCandidateDetector;
import io.atworks.specscan.analysis.support.rule.pack.InitialRulePacks;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleOutputService {
    private final CandidateToOutputAdapter adapter = new CandidateToOutputAdapter();
    private final ResponseMetadataAdapter responseMetadataAdapter = new ResponseMetadataAdapter();
    private final RequestBindingConditionAdapter requestBindingAdapter = new RequestBindingConditionAdapter();
    private final StructuralConditionAdapter structuralConditionAdapter = new StructuralConditionAdapter();

    public Map<String, EndpointRuleOutput> generate(StaticScanResult scan, RepositorySource source,
                                                    List<ApiConditionDraft> annotationConditions,
                                                    List<ApiCondition> structuralConditions) {
        Map<String, EndpointRuleOutput> outputs = new LinkedHashMap<>();

        FactGraphBuildResult build = new DefaultFactCodeGraphBuilder().build(
            scan, source, FactGraphTraversalBudget.defaults());
        GraphRuleEngine engine = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all());
        for (FactCodeGraph graph : build.graphs()) {
            ApiEndpoint endpoint = endpointFor(scan.endpoints(), graph);
            if (endpoint == null) continue;
            Set<String> methodIds = new LinkedHashSet<>();
            for (FactNode node : graph.nodes()) {
                if (node.type() == FactNodeType.API_METHOD || node.type() == FactNodeType.METHOD) {
                    methodIds.add(node.id());
                }
            }
            GraphRuleEngineResult evaluated = engine.evaluate(graph, new MethodScope(graph.graphId(), methodIds));
            outputs.put(OperationKey.of(endpoint).externalKey(),
                adapter.adapt(endpoint, evaluated.candidates().businessRules()));
        }

        List<ApiCondition> combinedConditions = combinedStructuralConditions(
            annotationConditions, structuralConditions);
        for (ApiEndpoint endpoint : scan.endpoints()) {
            String operationKey = OperationKey.of(endpoint).externalKey();
            EndpointRuleOutput output = outputs.getOrDefault(operationKey, EndpointRuleOutput.empty(endpoint.path()));
            output = requestBindingAdapter.augment(endpoint, output);
            output = structuralConditionAdapter.augment(endpoint, output, combinedConditions);
            outputs.put(operationKey, responseMetadataAdapter.augment(endpoint, output));
        }
        return outputs;
    }

    private List<ApiCondition> combinedStructuralConditions(List<ApiConditionDraft> annotationConditions,
                                                            List<ApiCondition> structuralConditions) {
        List<ApiCondition> combined = new ArrayList<>(structuralConditions);
        for (ApiConditionDraft draft : annotationConditions) {
            combined.add(new ApiCondition(ConditionLocation.UNKNOWN, draft.targetPath(), draft.operator(),
                draft.expected(), draft.evidence(), 1.0, "Source annotation", draft.sourceTrace(), null));
        }
        return combined;
    }

    private ApiEndpoint endpointFor(List<ApiEndpoint> endpoints, FactCodeGraph graph) {
        FactNode root = graph.nodes().stream().filter(node -> node.id().equals(graph.apiMethodNodeId()))
            .findFirst().orElse(null);
        if (root == null || !(root.payload() instanceof FactNodePayload.MethodPayload method)) return null;
        return endpoints.stream().filter(endpoint -> endpoint.controllerClass().equals(method.owner())
            && method.declarationSignature().startsWith(endpoint.controllerMethod() + "(")).findFirst().orElse(null);
    }
}
