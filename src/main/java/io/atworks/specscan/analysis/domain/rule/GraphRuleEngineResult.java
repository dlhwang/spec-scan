package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.candidate.CandidateResolutionResult;
import java.util.Objects;

public record GraphRuleEngineResult(CandidateResolutionResult candidates, RuleExecutionReport report) {
    public GraphRuleEngineResult {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(report, "report");
    }
}
