package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import java.util.List;

public record CandidateDetectionResult(List<PredicateCandidate> candidates,
                                       List<RuleExecutionDiagnostic> diagnostics) {
    public CandidateDetectionResult {
        candidates = List.copyOf(candidates);
        diagnostics = List.copyOf(diagnostics);
    }
}
