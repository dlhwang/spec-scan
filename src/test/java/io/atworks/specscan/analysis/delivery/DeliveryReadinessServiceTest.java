package io.atworks.specscan.analysis.delivery;

import io.atworks.specscan.analysis.domain.delivery.*;
import io.atworks.specscan.analysis.domain.output.OutputMigrationMode;
import io.atworks.specscan.analysis.support.delivery.DeliveryReadinessService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryReadinessServiceTest {
    private final DeliveryReadinessService service = new DeliveryReadinessService();

    @Test void keepsCompareModeWithoutRealProjectAndConsumerEvidence() {
        DeliveryReadiness result = service.evaluate(new DeliveryEvidence(true, false, false, true, true, 0, false));
        assertThat(result.newOnlyReady()).isFalse();
        assertThat(result.recommendedMode()).isEqualTo(OutputMigrationMode.COMPARE);
        assertThat(result.violations()).extracting(DeliveryViolation::code)
            .contains("ACTUAL_PROJECT_REGRESSION", "OUTPUT_CONSUMER_COMPATIBILITY");
    }

    @Test void permitsNewOnlyButNotLegacyRemovalBeforeObservationAndApproval() {
        DeliveryReadiness result = service.evaluate(new DeliveryEvidence(true, true, true, true, true, 0, false));
        assertThat(result.newOnlyReady()).isTrue();
        assertThat(result.legacyRemovalReady()).isFalse();
        assertThat(result.recommendedMode()).isEqualTo(OutputMigrationMode.NEW_ONLY);
        assertThat(result.violations()).extracting(DeliveryViolation::code)
            .containsExactly("NEW_ONLY_OBSERVATION", "LEGACY_REMOVAL_APPROVAL");
    }

    @Test void permitsLegacyRemovalOnlyAfterObservedNewOnlyRunAndExplicitApproval() {
        DeliveryReadiness result = service.evaluate(new DeliveryEvidence(true, true, true, true, true, 1, true));
        assertThat(result.legacyRemovalReady()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test void migrationModeParsingIsFailSafeAndKeepsExplicitRollback() {
        assertThat(OutputMigrationMode.configured(null)).isEqualTo(OutputMigrationMode.COMPARE);
        assertThat(OutputMigrationMode.configured("unknown")).isEqualTo(OutputMigrationMode.COMPARE);
        assertThat(OutputMigrationMode.configured(" legacy_only ")).isEqualTo(OutputMigrationMode.LEGACY_ONLY);
        assertThat(OutputMigrationMode.configured("new_only")).isEqualTo(OutputMigrationMode.NEW_ONLY);
    }
}
