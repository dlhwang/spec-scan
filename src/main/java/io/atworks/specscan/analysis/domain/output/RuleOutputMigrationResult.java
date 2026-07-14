package io.atworks.specscan.analysis.domain.output;

import java.util.*;

public record RuleOutputMigrationResult(Map<String, EndpointRuleOutput> outputs,
                                        List<CandidateOutputComparisonReport> comparisonReports) {
    public RuleOutputMigrationResult {
        outputs = Map.copyOf(Objects.requireNonNull(outputs));
        comparisonReports = List.copyOf(Objects.requireNonNull(comparisonReports));
    }

    public MigrationComparisonDocument comparisonDocument() {
        EnumMap<MigrationDifferenceKind, Long> counts = new EnumMap<>(MigrationDifferenceKind.class);
        for (MigrationDifferenceKind kind : MigrationDifferenceKind.values()) counts.put(kind, 0L);
        comparisonReports.stream().flatMap(report -> report.differences().stream())
            .forEach(difference -> counts.compute(difference.kind(), (kind, count) -> count + 1));
        boolean blocked = counts.get(MigrationDifferenceKind.LEGACY_ONLY) > 0
            || counts.get(MigrationDifferenceKind.CONFLICTING) > 0
            || counts.get(MigrationDifferenceKind.UNRESOLVED_BY_NEW_ENGINE) > 0
            || counts.get(MigrationDifferenceKind.LEGACY_SCOPE_UNRESOLVED) > 0;
        return new MigrationComparisonDocument(
            new MigrationComparisonSummary(comparisonReports.size(), counts, blocked), comparisonReports);
    }
}
