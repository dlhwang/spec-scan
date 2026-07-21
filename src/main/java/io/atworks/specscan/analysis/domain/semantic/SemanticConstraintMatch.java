package io.atworks.specscan.analysis.domain.semantic;

import io.atworks.specscan.analysis.domain.candidate.CandidateDiagnostic;
import io.atworks.specscan.analysis.domain.candidate.NormalizedConstraint;
import java.util.List;
import java.util.Objects;

public record SemanticConstraintMatch(NormalizedConstraint constraint,
                                      ResolutionQuality resolutionQuality,
                                      List<CandidateDiagnostic> diagnostics) {
    public SemanticConstraintMatch {
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(resolutionQuality, "resolutionQuality");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
