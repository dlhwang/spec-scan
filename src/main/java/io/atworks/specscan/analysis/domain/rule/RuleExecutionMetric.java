package io.atworks.specscan.analysis.domain.rule;

public record RuleExecutionMetric(String ruleId, int evaluationCount, int matchCount, int failureCount) {
    public RuleExecutionMetric {
        if (ruleId == null || ruleId.isBlank()) throw new IllegalArgumentException("ruleId is required");
        if (evaluationCount < 0 || matchCount < 0 || failureCount < 0
                || failureCount > evaluationCount) throw new IllegalArgumentException("invalid rule metric counts");
    }
}
