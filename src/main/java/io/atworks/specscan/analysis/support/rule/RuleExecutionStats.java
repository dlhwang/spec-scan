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

    public RuleExecutionStats(int registeredRules) { this.registeredRules = registeredRules; }
    public void predicates(int count) { evaluatedPredicates = count; }
    public void executed() { executedRules++; }
    public void matched() { matchedCandidates++; }
    public void failed(RuleExecutionDiagnostic diagnostic) { failedRuleExecutions++; diagnostics.add(diagnostic); }
    public void diagnostic(RuleExecutionDiagnostic diagnostic) { diagnostics.add(diagnostic); }
    public void deduplicated() { deduplicatedCandidates++; }
    public RuleExecutionReport report() {
        List<RuleExecutionDiagnostic> sorted = diagnostics.stream().sorted(Comparator
            .comparing(RuleExecutionDiagnostic::code)
            .thenComparing(d -> Objects.toString(d.ruleId(), ""))
            .thenComparing(d -> Objects.toString(d.predicateCandidateId(), ""))).toList();
        return new RuleExecutionReport(registeredRules, evaluatedPredicates, executedRules, matchedCandidates,
            failedRuleExecutions, deduplicatedCandidates, sorted);
    }
}
