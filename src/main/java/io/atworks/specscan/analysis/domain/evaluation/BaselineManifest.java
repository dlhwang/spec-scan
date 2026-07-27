package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;

public record BaselineManifest(int schemaVersion, String baselineVersion,
                               String corpusManifestVersion, String sourceSnapshotId,
                               List<BaselineEntry> entries, ApprovalMetadata approval) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public BaselineManifest {
        requireText(baselineVersion, "baselineVersion");
        requireText(corpusManifestVersion, "corpusManifestVersion");
        requireText(sourceSnapshotId, "sourceSnapshotId");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        Objects.requireNonNull(approval, "approval");
    }

    public enum Disposition { PRESERVE, REPLACE, UNSUPPORTED }
    public enum ApprovalState { PENDING_REVIEW, APPROVED, REJECTED }

    public record BaselineEntry(String entryId, BaselineObservation.ObservationIdentity exactIdentity,
                                BaselineObservation.ScenarioIdentity scenarioIdentity,
                                Disposition disposition, String rationale,
                                SemanticExpectation expectedObservation,
                                DiagnosticExpectation expectedDiagnostic,
                                String sourceObservation) {
        public BaselineEntry {
            requireText(entryId, "entryId");
            Objects.requireNonNull(disposition, "disposition");
        }
    }

    public record SemanticExpectation(String category, String effect, String constraintKind,
                                      String targetPath, String operator, List<String> expectedValues,
                                      String extractionStatus, String semanticStatus,
                                      String targetResolutionStatus, boolean evidenceRequired) {
        public SemanticExpectation {
            expectedValues = List.copyOf(Objects.requireNonNull(expectedValues, "expectedValues"));
        }
    }

    public record DiagnosticExpectation(String status, String diagnosticCode) {}

    public record ApprovalMetadata(ApprovalState state, String approvedBy, String approvedAt,
                                   String rationale) {
        public ApprovalMetadata { Objects.requireNonNull(state, "state"); }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
