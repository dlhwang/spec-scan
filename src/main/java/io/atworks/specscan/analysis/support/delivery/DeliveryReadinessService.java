package io.atworks.specscan.analysis.support.delivery;

import io.atworks.specscan.analysis.domain.delivery.*;
import io.atworks.specscan.analysis.domain.output.OutputMigrationMode;
import java.util.ArrayList;
import java.util.List;

public final class DeliveryReadinessService {
    public DeliveryReadiness evaluate(DeliveryEvidence evidence) {
        List<DeliveryViolation> violations = new ArrayList<>();
        require(violations, evidence.qualityGatePassed(), "QUALITY_GATE", "Unit 06 quality gate has not passed");
        require(violations, evidence.actualProjectRegressionPassed(), "ACTUAL_PROJECT_REGRESSION",
            "Actual-project regression evidence is missing");
        require(violations, evidence.outputConsumerCompatibilityPassed(), "OUTPUT_CONSUMER_COMPATIBILITY",
            "Output consumer compatibility has not been verified");
        require(violations, evidence.migrationDisagreementsReviewed(), "MIGRATION_DISAGREEMENT_REVIEW",
            "Legacy/new disagreements have not been reviewed");
        require(violations, evidence.rollbackVerified(), "ROLLBACK_VERIFICATION",
            "LEGACY_ONLY rollback has not been verified");

        boolean newOnlyReady = violations.isEmpty();
        if (newOnlyReady && evidence.successfulNewOnlyObservationRuns() == 0) {
            violations.add(new DeliveryViolation("NEW_ONLY_OBSERVATION", "No successful NEW_ONLY observation run exists"));
        }
        if (newOnlyReady && !evidence.legacyRemovalApproved()) {
            violations.add(new DeliveryViolation("LEGACY_REMOVAL_APPROVAL", "Legacy removal requires explicit approval"));
        }
        boolean legacyRemovalReady = newOnlyReady
            && evidence.successfulNewOnlyObservationRuns() > 0
            && evidence.legacyRemovalApproved();
        return new DeliveryReadiness(newOnlyReady, legacyRemovalReady,
            newOnlyReady ? OutputMigrationMode.NEW_ONLY : OutputMigrationMode.COMPARE, violations);
    }

    private void require(List<DeliveryViolation> violations, boolean condition, String code, String details) {
        if (!condition) violations.add(new DeliveryViolation(code, details));
    }
}
