package io.atworks.specscan.analysis.domain.output;

import java.util.*;

public record RuleOutputMigrationResult(Map<String, EndpointRuleOutput> outputs,
                                        List<CandidateOutputComparisonReport> comparisonReports) {
    public RuleOutputMigrationResult {
        outputs = Map.copyOf(Objects.requireNonNull(outputs));
        comparisonReports = List.copyOf(Objects.requireNonNull(comparisonReports));
    }
}
