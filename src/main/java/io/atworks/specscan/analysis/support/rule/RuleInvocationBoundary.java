package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.CandidateInvariantValidator;
import java.util.*;

public final class RuleInvocationBoundary {
    private final CandidateInvariantValidator validator = new CandidateInvariantValidator();

    public List<BusinessRuleCandidate> invoke(GraphRule rule, FactCodeGraph graph, PredicateCandidate predicate,
                                               RuleExecutionStats stats) {
        stats.executed();
        try {
            List<BusinessRuleCandidate> returned = rule.match(graph, predicate);
            if (returned == null) return failure(rule, predicate, stats, "RULE_RETURNED_NULL", null, "Rule returned null");
            List<BusinessRuleCandidate> valid = new ArrayList<>();
            for (BusinessRuleCandidate candidate : returned) {
                if (candidate == null) return failure(rule, predicate, stats, "RULE_RETURNED_INVALID_CANDIDATE", null, "Rule returned null candidate");
                try {
                    validator.validate(candidate);
                    if (!predicate.candidateId().equals(candidate.predicateCandidateId()) || !rule.id().equals(candidate.ruleId())) {
                        throw new IllegalArgumentException("candidate does not belong to invocation");
                    }
                } catch (IllegalArgumentException invalid) {
                    return failure(rule, predicate, stats, "RULE_RETURNED_INVALID_CANDIDATE", invalid,
                        Objects.toString(invalid.getMessage(), "Invalid candidate"));
                }
                valid.add(candidate);
            }
            return valid;
        } catch (RuntimeException exception) {
            return failure(rule, predicate, stats, "RULE_EXECUTION_FAILED", exception,
                Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
        }
    }
    private List<BusinessRuleCandidate> failure(GraphRule rule, PredicateCandidate predicate, RuleExecutionStats stats,
                                                 String code, RuntimeException exception, String message) {
        String safe = message.substring(0, Math.min(message.length(), 200));
        stats.failed(new RuleExecutionDiagnostic(RuleExecutionDiagnosticSeverity.ERROR, code, rule.id(),
            predicate.candidateId(), exception == null ? null : exception.getClass().getName(), safe));
        return List.of();
    }
}
