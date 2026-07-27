package io.atworks.specscan.analysis.domain.evaluation;

public record EvaluationProfile(String profileId, long singleRunTimeoutMillis,
                                long suiteTimeoutMillis) {
    public EvaluationProfile {
        if (profileId == null || profileId.isBlank()) throw new IllegalArgumentException("profileId is required");
        if (singleRunTimeoutMillis <= 0) throw new IllegalArgumentException("singleRunTimeoutMillis must be positive");
        if (suiteTimeoutMillis <= 0) throw new IllegalArgumentException("suiteTimeoutMillis must be positive");
        if (suiteTimeoutMillis < singleRunTimeoutMillis)
            throw new IllegalArgumentException("suite timeout cannot be shorter than a single run");
    }

    public static EvaluationProfile smoke() { return new EvaluationProfile("u01-smoke", 30_000, 90_000); }
    public static EvaluationProfile g01() { return new EvaluationProfile("u01-g01", 600_000, 1_800_000); }
}
