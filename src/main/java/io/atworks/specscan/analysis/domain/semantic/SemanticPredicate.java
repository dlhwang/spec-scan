package io.atworks.specscan.analysis.domain.semantic;

import io.atworks.specscan.analysis.domain.candidate.EvidenceRef;
import io.atworks.specscan.analysis.domain.candidate.CandidateDiagnostic;
import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import java.util.List;
import java.util.Objects;

public record SemanticPredicate(PredicateCandidate source, SemanticExpression expression,
                                FailurePolarity failurePolarity, String inputPath,
                                String methodSignature, List<EvidenceRef> evidence,
                                ResolutionQuality resolutionQuality,
                                List<CandidateDiagnostic> diagnostics) {
    public SemanticPredicate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(failurePolarity, "failurePolarity");
        Objects.requireNonNull(resolutionQuality, "resolutionQuality");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
