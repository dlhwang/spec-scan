package io.atworks.specscan.analysis.domain.output;

import java.util.*;

public record CandidateOutputComparisonReport(String endpointPath, List<MigrationDifference> differences) {
    public CandidateOutputComparisonReport {
        differences = List.copyOf(Objects.requireNonNull(differences));
    }
}
