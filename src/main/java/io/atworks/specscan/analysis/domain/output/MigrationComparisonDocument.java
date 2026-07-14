package io.atworks.specscan.analysis.domain.output;

import java.util.List;
import java.util.Objects;

public record MigrationComparisonDocument(MigrationComparisonSummary summary,
                                          List<CandidateOutputComparisonReport> operations) {
    public MigrationComparisonDocument {
        Objects.requireNonNull(summary);
        operations = List.copyOf(Objects.requireNonNull(operations));
    }
}
