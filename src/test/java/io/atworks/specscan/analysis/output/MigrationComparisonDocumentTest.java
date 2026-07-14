package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationComparisonDocumentTest {
    @Test void summarizesDifferencesAndBlocksUnsafeTransition() {
        CandidateOutputComparisonReport report = new CandidateOutputComparisonReport("GET", "/items", "C#get",
            List.of(new MigrationDifference(MigrationDifferenceKind.LEGACY_ONLY, "key", "details")));
        RuleOutputMigrationResult result = new RuleOutputMigrationResult(Map.of(), List.of(report));
        assertThat(result.comparisonDocument().summary().operationCount()).isEqualTo(1);
        assertThat(result.comparisonDocument().summary().counts().get(MigrationDifferenceKind.LEGACY_ONLY)).isEqualTo(1);
        assertThat(result.comparisonDocument().summary().newOnlyTransitionBlocked()).isTrue();
    }
}
