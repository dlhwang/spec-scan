package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;

public interface ReportedValidationCandidateDetector extends ValidationCandidateDetector {
    CandidateDetectionResult detectReported(FactCodeGraph graph, MethodScope scope);

    @Override
    default java.util.List<io.atworks.specscan.analysis.domain.candidate.PredicateCandidate> detect(
            FactCodeGraph graph, MethodScope scope) {
        return detectReported(graph, scope).candidates();
    }
}
