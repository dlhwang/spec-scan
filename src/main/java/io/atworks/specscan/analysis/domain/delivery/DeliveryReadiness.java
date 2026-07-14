package io.atworks.specscan.analysis.domain.delivery;

import io.atworks.specscan.analysis.domain.output.OutputMigrationMode;
import java.util.List;
import java.util.Objects;

public record DeliveryReadiness(
    boolean newOnlyReady,
    boolean legacyRemovalReady,
    OutputMigrationMode recommendedMode,
    List<DeliveryViolation> violations
) {
    public DeliveryReadiness {
        Objects.requireNonNull(recommendedMode);
        violations = List.copyOf(Objects.requireNonNull(violations));
    }
}
