package io.atworks.specscan.analysis.domain.output;

import java.util.Map;
import java.util.Objects;

public record MigrationComparisonSummary(int operationCount, Map<MigrationDifferenceKind, Long> counts,
                                         boolean newOnlyTransitionBlocked) {
    public MigrationComparisonSummary {
        counts = Map.copyOf(Objects.requireNonNull(counts));
    }
}
