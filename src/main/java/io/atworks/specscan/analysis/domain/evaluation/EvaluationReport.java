package io.atworks.specscan.analysis.domain.evaluation;

import java.util.*;

public record EvaluationReport(EvaluationMetrics metrics, List<EvaluationFailure> failures,
                               String deterministicFingerprint) {
    public EvaluationReport {
        Objects.requireNonNull(metrics); failures = List.copyOf(Objects.requireNonNull(failures));
        if (deterministicFingerprint == null || deterministicFingerprint.isBlank())
            throw new IllegalArgumentException("fingerprint is required");
    }
}
