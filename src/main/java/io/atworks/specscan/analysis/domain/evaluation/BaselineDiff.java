package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;

public final class BaselineDiff {
    private BaselineDiff() {}
    public enum ChangeType { ADDED, REMOVED, CHANGED, UNCHANGED }
    public enum Verdict { PASS, FAIL, REVIEW_REQUIRED }

    public record Change(String identity, ChangeType type, BaselineManifest.Disposition disposition,
                         BaselineObservation.SemanticObservation before,
                         BaselineObservation.SemanticObservation after,
                         Verdict verdict, String details) {
        public Change {
            if (identity == null || identity.isBlank()) throw new IllegalArgumentException("identity is required");
            Objects.requireNonNull(type, "type"); Objects.requireNonNull(verdict, "verdict");
        }
    }

    public record ChangeProposal(String proposalId, String state, List<Change> changes, String rationale) {
        public ChangeProposal {
            if (proposalId == null || proposalId.isBlank()) throw new IllegalArgumentException("proposalId is required");
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        }
    }

    public record Result(List<Change> changes, List<BaselineDiagnostic> diagnostics,
                         ChangeProposal proposal, Verdict verdict) {
        public Result {
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            Objects.requireNonNull(verdict, "verdict");
        }
        public static Result reviewProposal(ChangeProposal proposal, List<BaselineDiagnostic> diagnostics) {
            return new Result(proposal.changes(), diagnostics, proposal, Verdict.REVIEW_REQUIRED);
        }
    }
}
