package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.candidate.CandidateDiagnostic;
import io.atworks.specscan.analysis.domain.candidate.ExtractionStatus;
import java.util.List;
import java.util.Objects;

public record FailureOutcomeDecision(boolean failure, ExtractionStatus extractionStatus,
                                     List<CandidateDiagnostic> diagnostics) {
    public FailureOutcomeDecision {
        Objects.requireNonNull(extractionStatus, "extractionStatus");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (failure && extractionStatus != ExtractionStatus.EXTRACTED && diagnostics.isEmpty()) {
            throw new IllegalArgumentException("partial failure decision requires diagnostic");
        }
    }
    public static FailureOutcomeDecision notFailure() {
        return new FailureOutcomeDecision(false, ExtractionStatus.EXTRACTED, List.of());
    }
}
