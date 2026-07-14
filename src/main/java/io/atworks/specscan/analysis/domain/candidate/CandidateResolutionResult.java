package io.atworks.specscan.analysis.domain.candidate;

import java.util.List;

public record CandidateResolutionResult(String graphId, List<PredicateCandidate> predicates,
                                        List<BusinessRuleCandidate> businessRules,
                                        List<CandidateDiagnostic> diagnostics) {
    public CandidateResolutionResult {
        if (graphId == null || graphId.isBlank()) throw new IllegalArgumentException("graphId is required");
        predicates = List.copyOf(predicates);
        businessRules = List.copyOf(businessRules);
        diagnostics = List.copyOf(diagnostics);
    }
}
