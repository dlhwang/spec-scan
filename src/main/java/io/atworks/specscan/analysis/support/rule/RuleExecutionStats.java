package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class RuleExecutionStats {
    private final int registeredRules;
    private int evaluatedPredicates;
    private int executedRules;
    private int matchedCandidates;
    private int failedRuleExecutions;
    private int deduplicatedCandidates;
    private final List<RuleExecutionDiagnostic> diagnostics = new ArrayList<>();
    private final Map<String, MutableRuleMetric> ruleMetrics = new HashMap<>();

    public RuleExecutionStats(int registeredRules) { this.registeredRules = registeredRules; }
    public void predicates(int count) { evaluatedPredicates = count; }
    public void executed(String ruleId) { executedRules++; metric(ruleId).evaluations++; }
    public void matched(String ruleId) { matchedCandidates++; metric(ruleId).matches++; }
    public void failed(RuleExecutionDiagnostic diagnostic) {
        failedRuleExecutions++;
        metric(diagnostic.ruleId()).failures++;
        diagnostics.add(diagnostic);
    }
    public void diagnostic(RuleExecutionDiagnostic diagnostic) { diagnostics.add(diagnostic); }
    public void deduplicated() { deduplicatedCandidates++; }
    public RuleExecutionReport report() {
        List<RuleExecutionDiagnostic> sorted = diagnostics.stream().sorted(Comparator
            .comparing(RuleExecutionDiagnostic::code)
            .thenComparing(d -> Objects.toString(d.ruleId(), ""))
            .thenComparing(d -> Objects.toString(d.predicateCandidateId(), ""))).toList();
        List<RuleExecutionMetric> metrics = ruleMetrics.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> new RuleExecutionMetric(entry.getKey(), entry.getValue().evaluations,
                entry.getValue().matches, entry.getValue().failures)).toList();
        return new RuleExecutionReport(registeredRules, evaluatedPredicates, executedRules, matchedCandidates,
            failedRuleExecutions, deduplicatedCandidates, metrics, sorted);
    }
    private MutableRuleMetric metric(String ruleId) {
        return ruleMetrics.computeIfAbsent(ruleId, ignored -> new MutableRuleMetric());
    }
    private static final class MutableRuleMetric { int evaluations; int matches; int failures; }
}
