package io.atworks.specscan.analysis.domain.rule;

import java.util.List;
import java.util.Objects;

public record RuleExecutionReport(int registeredRules, int evaluatedPredicates, int executedRules,
                                  int matchedCandidates, int failedRuleExecutions,
                                  int deduplicatedCandidates, List<RuleExecutionDiagnostic> diagnostics) {
    public RuleExecutionReport {
        if (registeredRules < 0 || evaluatedPredicates < 0 || executedRules < 0 || matchedCandidates < 0
            || failedRuleExecutions < 0 || deduplicatedCandidates < 0) {
            throw new IllegalArgumentException("report counts cannot be negative");
        }
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        long maximumExecutions = (long) registeredRules * evaluatedPredicates;
        if (executedRules > maximumExecutions || failedRuleExecutions > executedRules) {
            throw new IllegalArgumentException("invalid report counts");
        }
    }
}
