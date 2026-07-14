package io.atworks.specscan.analysis.domain.delivery;

public record DeliveryEvidence(
    boolean qualityGatePassed,
    boolean actualProjectRegressionPassed,
    boolean outputConsumerCompatibilityPassed,
    boolean migrationDisagreementsReviewed,
    boolean rollbackVerified,
    int successfulNewOnlyObservationRuns,
    boolean legacyRemovalApproved
) {
    public DeliveryEvidence {
        if (successfulNewOnlyObservationRuns < 0) {
            throw new IllegalArgumentException("successfulNewOnlyObservationRuns must not be negative");
        }
    }
}
