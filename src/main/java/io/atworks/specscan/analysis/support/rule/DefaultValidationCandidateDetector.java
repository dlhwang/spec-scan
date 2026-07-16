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
        addPasswordTrailingFailures(graph, conditionIds, nodes, candidates);
        addValidationSinkFailures(graph, scope, nodes, candidates);
        return new CandidateDetectionResult(candidates, reportDiagnostics);
    }

    private void addPasswordTrailingFailures(FactCodeGraph graph, Set<String> conditionIds,
                                             Map<String, FactNode> nodes, List<PredicateCandidate> candidates) {
        Set<String> existing = new HashSet<>();
        candidates.forEach(candidate -> existing.add(candidate.conditionNodeId()));
        for (String conditionId : conditionIds.stream().sorted().toList()) {
            if (existing.contains(conditionId)) continue;
            FactNode passwordCall = descendants(graph, nodes, conditionId).stream()
                .filter(node -> node.payload() instanceof FactNodePayload.MethodCallPayload call
                    && "matches".equals(call.methodName()) && isPasswordEncoder(node.typeResolution()))
                .findFirst().orElse(null);
            FactNode mismatch = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(conditionId)
                    && edge.type() == FactEdgeType.ELSE_OUTCOME)
                .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull).findFirst().orElse(null);
            if (passwordCall == null || mismatch == null) continue;
            FactNode condition = nodes.get(conditionId);
            candidates.add(new PredicateCandidate(ids.forPredicate(graph.graphId(), conditionId), graph.graphId(),
                conditionId, PredicateType.COMPOSITE, ExtractionStatus.EXTRACTED,
                List.of(evidenceMapper.fromFact(condition, EvidenceRole.PREDICATE),
                    evidenceMapper.fromFact(mismatch, EvidenceRole.FAILURE_OUTCOME)), List.of()));
        }
    }

    private List<FactNode> descendants(FactCodeGraph graph, Map<String, FactNode> nodes, String source) {
        List<FactNode> result = new ArrayList<>(); Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>(); pending.add(source);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst(); if (!visited.add(current)) continue;
            graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(current)
                    && edge.type() == FactEdgeType.OPERAND_OF).forEach(edge -> {
                FactNode node = nodes.get(edge.targetNodeId());
                if (node != null) { result.add(node); pending.addLast(node.id()); }
            });
        }
        return result;
    }

    private boolean isPasswordEncoder(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.contains("org.springframework.security.crypto.password")
            && signature.endsWith(".matches(java.lang.CharSequence, java.lang.String)");
    }

    private void addImplicitOptionalFailures(FactCodeGraph graph, MethodScope scope, Map<String, FactNode> nodes,
                                              List<PredicateCandidate> candidates) {
        Set<String> scopedCalls = new HashSet<>();
        for (FactEdge edge : graph.edges()) if (edge.type() == FactEdgeType.CALLS
                && scope.methodNodeIds().contains(edge.sourceNodeId())) scopedCalls.add(edge.targetNodeId());
        for (String callId : scopedCalls.stream().sorted().toList()) {
            FactNode call = nodes.get(callId);
            if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                    || !"orElseThrow".equals(payload.methodName())
                    || (!isJdkOptional(call.typeResolution()) && !hasResolvedSpringDataReceiver(graph, nodes, call.id()))) continue;
            candidates.add(new PredicateCandidate(ids.forPredicate(graph.graphId(), call.id()), graph.graphId(),
                call.id(), PredicateType.LOOKUP_CHAIN, ExtractionStatus.EXTRACTED,
                List.of(evidenceMapper.fromFact(call, EvidenceRole.PREDICATE),
                    evidenceMapper.fromFact(call, EvidenceRole.FAILURE_OUTCOME)), List.of()));
        }
    }

    private boolean hasResolvedSpringDataReceiver(FactCodeGraph graph, Map<String, FactNode> nodes, String callId) {
        for (FactEdge edge : graph.edges()) {
            if (!edge.sourceNodeId().equals(callId) || edge.type() != FactEdgeType.OPERAND_OF
                    || !"RECEIVER".equals(edge.role())) continue;
            FactNode receiver = nodes.get(edge.targetNodeId());
            if (receiver == null || !(receiver.payload() instanceof FactNodePayload.MethodCallPayload payload)
                    || !"findById".equals(payload.methodName())) continue;
            String signature = receiver.typeResolution().resolvedSignature();
            if (receiver.typeResolution().status() == TypeResolutionStatus.RESOLVED && signature != null
                    && signature.contains("org.springframework.data.repository") && signature.contains(".findById(")) return true;
        }
        return false;
    }

    private boolean isJdkOptional(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.contains("java.util.Optional") && signature.contains(".orElseThrow(");
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

    private void addValidationSinkFailures(FactCodeGraph graph, MethodScope scope, Map<String, FactNode> nodes,
                                           List<PredicateCandidate> candidates) {
        Set<String> scopedCalls = new HashSet<>();
        for (FactEdge edge : graph.edges()) {
            if (edge.type() == FactEdgeType.CALLS && scope.methodNodeIds().contains(edge.sourceNodeId())) {
                scopedCalls.add(edge.targetNodeId());
            }
        }
        for (String callId : scopedCalls.stream().sorted().toList()) {
            FactNode call = nodes.get(callId);
            if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)) continue;
            String name = payload.methodName();
            String signature = call.typeResolution().resolvedSignature();
            boolean isSink = false;
            if (call.typeResolution().status() == TypeResolutionStatus.RESOLVED && signature != null) {
                if (signature.startsWith("java.util.Objects.requireNonNull")
                        || signature.startsWith("com.google.common.base.Preconditions.checkNotNull")
                        || signature.startsWith("org.springframework.util.Assert.notNull")) {
                    isSink = true;
                }
            } else {
                String text = call.snippet();
                if (text != null && (text.contains("requireNonNull") || text.contains("checkNotNull") || text.contains("notNull"))) {
                    isSink = true;
                }
            }
            if (isSink) {
                if (candidates.stream().anyMatch(c -> c.conditionNodeId().equals(call.id()))) continue;
                candidates.add(new PredicateCandidate(
                    ids.forPredicate(graph.graphId(), call.id()),
                    graph.graphId(),
                    call.id(),
                    PredicateType.COMPOSITE,
                    ExtractionStatus.EXTRACTED,
                    List.of(evidenceMapper.fromFact(call, EvidenceRole.PREDICATE),
                            evidenceMapper.fromFact(call, EvidenceRole.FAILURE_OUTCOME)),
                    List.of()
                ));
            }
        }
    }

    private void validateScope(FactCodeGraph graph, MethodScope scope) {
        if (!graph.graphId().equals(scope.graphId())) throw new IllegalArgumentException("INVALID_METHOD_SCOPE");
        Map<String, FactNodeType> types = new HashMap<>();
        graph.nodes().forEach(node -> types.put(node.id(), node.type()));
        for (String id : scope.methodNodeIds()) if (types.get(id) != FactNodeType.API_METHOD
                && types.get(id) != FactNodeType.METHOD && types.get(id) != FactNodeType.CONSTRUCTOR) {
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
