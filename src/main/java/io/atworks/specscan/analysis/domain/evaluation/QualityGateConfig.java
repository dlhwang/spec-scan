package io.atworks.specscan.analysis.domain.evaluation;

public record QualityGateConfig(double minimumCandidatePrecision, double minimumSemanticPrecision,
                                double minimumSupportedScopeRecall, double minimumEvidenceTraceRate,
                                int maximumFalsePositives, int minimumCrossDatasetReusedRules,
                                int maximumUnsupportedResolved, int maximumProductionHardcodedValues,
                                int maximumUnintendedRegressions) {
    public static QualityGateConfig initial() {
        return new QualityGateConfig(1.0, 1.0, 0.8, 1.0, 0, 1, 0, 0, 0);
    }
}
