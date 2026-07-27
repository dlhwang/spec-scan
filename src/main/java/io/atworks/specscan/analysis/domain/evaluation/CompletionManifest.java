package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;

public record CompletionManifest(int schemaVersion, String bundleId, String inputIdentity,
                                 EvaluationResultBundle.OverallStatus overallStatus,
                                 List<PayloadDigest> payloads, String createdAt, String completionMarker) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String MARKER = "SPEC_SCAN_U01_COMPLETE";

    public CompletionManifest {
        if (bundleId == null || bundleId.isBlank()) throw new IllegalArgumentException("bundleId is required");
        if (inputIdentity == null || inputIdentity.isBlank()) throw new IllegalArgumentException("inputIdentity is required");
        Objects.requireNonNull(overallStatus, "overallStatus");
        payloads = List.copyOf(Objects.requireNonNull(payloads, "payloads"));
        if (!MARKER.equals(completionMarker)) throw new IllegalArgumentException("invalid completion marker");
    }

    public record PayloadDigest(String fileName, String sha256) {
        public PayloadDigest {
            if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName is required");
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("sha256 must be lowercase hex");
        }
    }
}
