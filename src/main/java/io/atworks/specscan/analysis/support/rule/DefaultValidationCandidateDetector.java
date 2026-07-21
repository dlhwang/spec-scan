package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.*;

public final class DefaultValidationCandidateDetector implements ReportedValidationCandidateDetector {
    private final List<FailureOutcomePolicy> policies;
    private final List<ValidationSeedContributor> seedContributors;
    private final DeterministicCandidateIdGenerator ids = new DeterministicCandidateIdGenerator();
    private final PredicateTypeClassifier classifier = new PredicateTypeClassifier();
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();
    private final DelegatedBooleanPredicatePromoter delegatedBooleanPromoter =
        new DelegatedBooleanPredicatePromoter();

    public DefaultValidationCandidateDetector(List<FailureOutcomePolicy> policies) {
        this(policies, List.of(new ImplicitOptionalSeedContributor(),
            new PasswordFailureSeedContributor(), new StandardGuardSeedContributor()));
    }

    public DefaultValidationCandidateDetector(List<FailureOutcomePolicy> policies,
                                              List<ValidationSeedContributor> seedContributors) {
        this.policies = List.copyOf(policies);
        this.seedContributors = List.copyOf(seedContributors);
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
        for (ValidationSeedContributor contributor : seedContributors) {
            try {
                List<PredicateCandidate> contributed = contributor.contribute(graph, scope, List.copyOf(candidates));
                if (contributed == null) throw new IllegalStateException("contributor returned null");
                for (PredicateCandidate candidate : contributed) {
                    if (candidate != null && candidates.stream().noneMatch(existing ->
                            existing.conditionNodeId().equals(candidate.conditionNodeId()))) candidates.add(candidate);
                }
            } catch (RuntimeException exception) {
                reportDiagnostics.add(new RuleExecutionDiagnostic(RuleExecutionDiagnosticSeverity.ERROR,
                    "SEED_CONTRIBUTOR_FAILED", contributor.id(), null,
                    exception.getClass().getName(), safeMessage(exception)));
            }
        }
        return new CandidateDetectionResult(delegatedBooleanPromoter.promote(graph, candidates), reportDiagnostics);
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
