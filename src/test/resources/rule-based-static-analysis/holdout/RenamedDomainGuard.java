package fixtures.holdout;

public final class RenamedDomainGuard {

    public void requireEligible(Submission submission) {
        if (submission.phase() != Phase.ELIGIBLE
            && submission.phase() != Phase.REVIEWABLE) {
            throw new IllegalArgumentException("submission cannot proceed");
        }
    }

    public record Submission(Phase phase) {
    }

    public enum Phase {
        ELIGIBLE,
        REVIEWABLE,
        ARCHIVED
    }
}

