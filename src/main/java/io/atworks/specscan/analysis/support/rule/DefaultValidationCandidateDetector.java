package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.*;

public final class DefaultValidationCandidateDetector implements ReportedValidationCandidateDetector {
    private final List<FailureOutcomePolicy> policies;
    private final DeterministicCandidateIdGenerator ids = new DeterministicCandidateIdGenerator();
    private final PredicateTypeClassifier classifier = new PredicateTypeClassifier();
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();

    public DefaultValidationCandidateDetector(List<FailureOutcomePolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    @Override
    public CandidateDetectionResult detectReported(FactCodeGraph graph, MethodScope scope) {
        validateScope(graph, scope);
        Map<String, FactNode> nodes = new HashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        Set<String> conditionIds = new HashSet<>();
        for (FactEdge edge : graph.edges()) if (edge.type() == FactEdgeType.CONTROLS
                && scope.methodNodeIds().contains(edge.sourceNodeId())) conditionIds.add(edge.targetNodeId());

        List<PredicateCandidate> candidates = new ArrayList<>();
        List<RuleExecutionDiagnostic> reportDiagnostics = new ArrayList<>();
        for (String conditionId : conditionIds.stream().sorted().toList()) {
            FactNode condition = nodes.get(conditionId);
            List<EvidenceRef> evidence = new ArrayList<>();
            evidence.add(evidenceMapper.fromFact(condition, EvidenceRole.PREDICATE));
            List<CandidateDiagnostic> diagnostics = new ArrayList<>();
            ExtractionStatus status = ExtractionStatus.EXTRACTED;
            boolean failure = false;
            for (FactEdge edge : graph.edges()) {
                if (!edge.sourceNodeId().equals(conditionId) || !isOutcomeEdge(edge.type())) continue;
                FactNode outcome = nodes.get(edge.targetNodeId());
                if (outcome == null) continue;
                if (outcome.type() == FactNodeType.THROW) {
                    failure = true;
                    evidence.add(evidenceMapper.fromFact(outcome, EvidenceRole.FAILURE_OUTCOME));
                } else if (outcome.type() == FactNodeType.RETURN) {
                    PolicyEvaluation evaluation = evaluatePolicies(graph, condition, outcome, reportDiagnostics);
                    if (evaluation.failure()) {
                        failure = true;
                        evidence.add(evidenceMapper.fromFact(outcome, EvidenceRole.FAILURE_OUTCOME));
                        diagnostics.addAll(evaluation.diagnostics());
                        if (evaluation.status() != ExtractionStatus.EXTRACTED) status = evaluation.status();
                    }
                }
            }
            if (failure) candidates.add(new PredicateCandidate(ids.forPredicate(graph.graphId(), condition.id()),
                graph.graphId(), condition.id(), classifier.classify(graph, condition), status, evidence, diagnostics));
        }
        addImplicitOptionalFailures(graph, scope, nodes, candidates);
        return new CandidateDetectionResult(candidates, reportDiagnostics);
    }

    private void addImplicitOptionalFailures(FactCodeGraph graph, MethodScope scope, Map<String, FactNode> nodes,
                                              List<PredicateCandidate> candidates) {
        Set<String> scopedCalls = new HashSet<>();
        for (FactEdge edge : graph.edges()) if (edge.type() == FactEdgeType.CALLS
                && scope.methodNodeIds().contains(edge.sourceNodeId())) scopedCalls.add(edge.targetNodeId());
        for (String callId : scopedCalls.stream().sorted().toList()) {
            FactNode call = nodes.get(callId);
            if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                    || !"orElseThrow".equals(payload.methodName()) || !isJdkOptional(call.typeResolution())) continue;
            candidates.add(new PredicateCandidate(ids.forPredicate(graph.graphId(), call.id()), graph.graphId(),
                call.id(), PredicateType.LOOKUP_CHAIN, ExtractionStatus.EXTRACTED,
                List.of(evidenceMapper.fromFact(call, EvidenceRole.PREDICATE),
                    evidenceMapper.fromFact(call, EvidenceRole.FAILURE_OUTCOME)), List.of()));
        }
    }

    private boolean isJdkOptional(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.startsWith("java.util.Optional.") && signature.contains("orElseThrow(");
    }

    private PolicyEvaluation evaluatePolicies(FactCodeGraph graph, FactNode condition, FactNode outcome,
                                                List<RuleExecutionDiagnostic> reportDiagnostics) {
        for (FailureOutcomePolicy policy : policies) {
            try {
                FailureOutcomeDecision decision = policy.evaluate(graph, condition, outcome);
                if (decision == null) throw new IllegalStateException("policy returned null");
                if (decision.failure()) return new PolicyEvaluation(true, decision.extractionStatus(), decision.diagnostics());
            } catch (RuntimeException exception) {
                reportDiagnostics.add(new RuleExecutionDiagnostic(RuleExecutionDiagnosticSeverity.ERROR,
                    "FAILURE_POLICY_FAILED", policy.id(), null, exception.getClass().getName(), safeMessage(exception)));
            }
        }
        return new PolicyEvaluation(false, ExtractionStatus.EXTRACTED, List.of());
    }
    private void validateScope(FactCodeGraph graph, MethodScope scope) {
        if (!graph.graphId().equals(scope.graphId())) throw new IllegalArgumentException("INVALID_METHOD_SCOPE");
        Map<String, FactNodeType> types = new HashMap<>();
        graph.nodes().forEach(node -> types.put(node.id(), node.type()));
        for (String id : scope.methodNodeIds()) if (types.get(id) != FactNodeType.API_METHOD && types.get(id) != FactNodeType.METHOD) {
            throw new IllegalArgumentException("INVALID_METHOD_SCOPE");
        }
    }
    private boolean isOutcomeEdge(FactEdgeType type) { return type == FactEdgeType.THEN_OUTCOME || type == FactEdgeType.ELSE_OUTCOME; }
    private String safeMessage(RuntimeException exception) {
        String message = Objects.toString(exception.getMessage(), exception.getClass().getSimpleName());
        return message.substring(0, Math.min(message.length(), 200));
    }
    private record PolicyEvaluation(boolean failure, ExtractionStatus status, List<CandidateDiagnostic> diagnostics) {}
}
