package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphDiagnostic;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.FactNodePayload;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.output.CandidateOutputDiagnostic;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import io.atworks.specscan.analysis.domain.rule.GraphRuleEngine;
import io.atworks.specscan.analysis.domain.rule.GraphRuleEngineResult;
import io.atworks.specscan.analysis.domain.rule.MethodScope;
import io.atworks.specscan.analysis.support.output.CandidateToOutputAdapter;
import io.atworks.specscan.analysis.support.output.RequestBindingConditionAdapter;
import io.atworks.specscan.analysis.support.output.ResponseMetadataAdapter;
import io.atworks.specscan.analysis.support.output.ResponseInvariantAdapter;
import io.atworks.specscan.analysis.support.rule.DefaultGraphRuleEngine;
import io.atworks.specscan.analysis.support.rule.DefaultValidationCandidateDetector;
import io.atworks.specscan.analysis.support.rule.pack.InitialRulePacks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;

public final class RuleOutputService {
    private final CandidateToOutputAdapter adapter = new CandidateToOutputAdapter();
    private final ResponseMetadataAdapter responseMetadataAdapter = new ResponseMetadataAdapter();
    private final RequestBindingConditionAdapter requestBindingAdapter = new RequestBindingConditionAdapter();
    private final ResponseInvariantAdapter responseInvariantAdapter = new ResponseInvariantAdapter();

    public Map<String, EndpointRuleOutput> generate(ScanAnalysisContext context) {
        return generate(context.scanResult(), context.factGraphs(),
            context.validationResult().directConditions());
    }

    public Map<String, EndpointRuleOutput> generate(StaticScanResult scan, FactGraphBuildResult build,
                                                    List<ApiConditionDraft> annotationConditions) {
        Map<String, EndpointRuleOutput> outputs = new LinkedHashMap<>();
        List<io.atworks.specscan.analysis.domain.candidate.BusinessRuleCandidate> allCandidates = new ArrayList<>();

        GraphRuleEngine engine = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all());

        // [첫 번째 루프] FactCodeGraph 단위 루프:
        // 그래프 탐색(BFS)으로 호출 스코프를 수집하고, GraphRuleEngine을 실행하여 비즈니스 규칙 후보(BusinessRuleCandidate)를 1차 도출합니다.
        for (FactCodeGraph graph : build.graphs()) {
            ApiEndpoint endpoint = endpointFor(scan.endpoints(), graph);
            if (endpoint == null) continue;
            Set<String> methodIds = new LinkedHashSet<>();
            methodIds.add(graph.apiMethodNodeId());
            
            // 1-1. BFS 탐색을 통한 메서드/생성자 스코프(methodIds) 수집
            Deque<String> pending = new ArrayDeque<>();
            pending.add(graph.apiMethodNodeId());
            Set<String> visited = new HashSet<>();
            Map<String, FactNode> nodeMap = new HashMap<>();
            graph.nodes().forEach(n -> nodeMap.put(n.id(), n));
            
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                if (!visited.add(current)) continue;
                for (io.atworks.specscan.analysis.domain.fact.FactEdge edge : graph.edges()) {
                    String neighborId = null;
                    if (edge.sourceNodeId().equals(current)) {
                        neighborId = edge.targetNodeId();
                    } else if (edge.targetNodeId().equals(current)) {
                        neighborId = edge.sourceNodeId();
                    }
                    if (neighborId != null) {
                        FactNode neighborNode = nodeMap.get(neighborId);
                        if (neighborNode != null) {
                            if (neighborNode.type() == FactNodeType.API_METHOD) {
                                continue;
                            }
                            if (neighborNode.type() == FactNodeType.METHOD || neighborNode.type() == FactNodeType.CONSTRUCTOR) {
                                methodIds.add(neighborId);
                            }
                            pending.addLast(neighborId);
                        }
                    }
                }
            }
            
            // 1-2. 수집된 스코프(MethodScope) 기반 룰 엔진 평가 및 1차 EndpointRuleOutput 생성
            GraphRuleEngineResult evaluated = engine.evaluate(graph, new MethodScope(graph.graphId(), methodIds));
            allCandidates.addAll(evaluated.candidates().businessRules());
            outputs.put(OperationKey.of(endpoint).externalKey(),
                adapter.adapt(endpoint, evaluated.candidates().businessRules()));
        }

        // [두 번째 루프] ApiEndpoint 단위 루프:
        // 1차 룰 결과에 어노테이션 기반 검증 조건(ApiConditionDraft), 빌드 진단 정보, 응답 불변식 및 메타데이터를 결합(Augment)합니다.
        for (ApiEndpoint endpoint : scan.endpoints()) {
            String operationKey = OperationKey.of(endpoint).externalKey();
            EndpointRuleOutput output = outputs.getOrDefault(operationKey, EndpointRuleOutput.empty(endpoint.path()));

            // 2-1. 어노테이션 기반 검증 조건(@NotNull, @Size 등 ApiConditionDraft) 결합
            output = requestBindingAdapter.augment(endpoint, output, annotationConditions);

            // 2-2. 그래프 빌드 과정의 진단/경고 정보 결합
            output = withBuildDiagnostics(endpoint, output, build.diagnostics());
            
            // 2-3. 해당 엔드포인트의 FactCodeGraph를 매핑하여 응답 불변식(Response Invariant) 결합
            final String currentKey = operationKey;
            FactCodeGraph graph = build.graphs().stream()
                .filter(g -> {
                    ApiEndpoint ep = endpointFor(scan.endpoints(), g);
                    return ep != null && OperationKey.of(ep).externalKey().equals(currentKey);
                })
                .findFirst().orElse(null);
            if (graph != null) {
                output = responseInvariantAdapter.augment(endpoint, output, graph, allCandidates);
            }

            // 2-4. 응답 메타데이터 결합 후 최종 결과 갱신
            outputs.put(operationKey, responseMetadataAdapter.augment(endpoint, output, graph));
        }
        return outputs;
    }

    private EndpointRuleOutput withBuildDiagnostics(ApiEndpoint endpoint, EndpointRuleOutput output,
                                                    List<FactGraphDiagnostic> diagnostics) {
        List<CandidateOutputDiagnostic> merged = new ArrayList<>(output.diagnostics());
        diagnostics.stream()
            .filter(diagnostic -> belongsTo(endpoint, diagnostic))
            .forEach(diagnostic -> merged.add(new CandidateOutputDiagnostic(
                "FACT_GRAPH_" + diagnostic.reason(),
                diagnostic.details(),
                "fact-graph:" + diagnostic.apiMethod(),
                null
            )));
        return new EndpointRuleOutput(output.endpointPath(), output.requestPreconditions(),
            output.responseAssertions(), output.externalStatePrerequisites(),
            output.excludedBusinessRules(), merged);
    }

    private boolean belongsTo(ApiEndpoint endpoint, FactGraphDiagnostic diagnostic) {
        String api = diagnostic.apiMethod();
        return api != null && api.startsWith(endpoint.controllerClass() + "." + endpoint.controllerMethod());
    }

    private ApiEndpoint endpointFor(List<ApiEndpoint> endpoints, FactCodeGraph graph) {
        FactNode root = graph.nodes().stream().filter(node -> node.id().equals(graph.apiMethodNodeId()))
            .findFirst().orElse(null);
        if (root == null || !(root.payload() instanceof FactNodePayload.MethodPayload method)) return null;
        return endpoints.stream().filter(endpoint -> endpoint.controllerClass().equals(method.owner())
            && method.declarationSignature().startsWith(endpoint.controllerMethod() + "(")).findFirst().orElse(null);
    }
}
