package io.atworks.specscan.analysis.domain.semantic;

import io.atworks.specscan.analysis.domain.candidate.CandidateDiagnostic;
import io.atworks.specscan.analysis.domain.candidate.NormalizedConstraint;
import java.util.List;
import java.util.Objects;

public record CompositeConstraintMatch(List<NormalizedConstraint> activationGuards,
                                       List<SemanticConstraintMatch> requirements,
                                       ResolutionQuality resolutionQuality,
                                       List<CandidateDiagnostic> diagnostics) {
    public CompositeConstraintMatch {
        activationGuards = List.copyOf(Objects.requireNonNull(activationGuards, "activationGuards"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        Objects.requireNonNull(resolutionQuality, "resolutionQuality");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
