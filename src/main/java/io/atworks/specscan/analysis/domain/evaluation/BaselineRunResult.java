package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;

public record BaselineRunResult(RunStatus status, BaselineObservation.Snapshot snapshot,
                                BaselineDiff.Result diff, List<BaselineDiagnostic> diagnostics,
                                BaselineObservation.MetricsSnapshot metrics, int analyzerInvocations) {
    public enum RunStatus { REVIEW_REQUIRED, PASS, FAILED_PREFLIGHT, FAILED_TIMEOUT, FAILED_ANALYSIS }

    public BaselineRunResult {
        Objects.requireNonNull(status, "status");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(metrics, "metrics");
        if (analyzerInvocations < 0) throw new IllegalArgumentException("analyzerInvocations cannot be negative");
    }

    public boolean successfulAnalysis() {
        return status == RunStatus.REVIEW_REQUIRED || status == RunStatus.PASS;
    }
}
