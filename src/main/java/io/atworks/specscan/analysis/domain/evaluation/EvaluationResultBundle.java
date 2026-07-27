package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;

public record EvaluationResultBundle(int schemaVersion, String bundleId, String inputIdentity,
                                     OverallStatus overallStatus, BaselineRunResult runResult,
                                     DeterminismResult determinism, List<BaselineDiagnostic> diagnostics) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public enum OverallStatus { PASS, REVIEW_REQUIRED, FAIL }

    public EvaluationResultBundle {
        if (bundleId == null || bundleId.isBlank()) throw new IllegalArgumentException("bundleId is required");
        if (inputIdentity == null || inputIdentity.isBlank()) throw new IllegalArgumentException("inputIdentity is required");
        Objects.requireNonNull(overallStatus, "overallStatus"); Objects.requireNonNull(runResult, "runResult");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public record DeterminismResult(boolean deterministic, int invocationCount,
                                    List<String> differences) {
        public DeterminismResult {
            differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
        }
    }
}
