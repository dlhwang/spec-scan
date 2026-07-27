package io.atworks.specscan.analysis.domain.evaluation;

import java.util.*;

public record CrossRepositoryEvaluationReport(YamlEvaluationVerdict verdict, int repositories,
        int endpoints, int yamlResolved, int javaResolved, int exactMatches,
        int fallbackCount, int holdoutMatches, List<String> diagnostics) {
    public CrossRepositoryEvaluationReport {
        if (repositories < 0 || endpoints < 0 || yamlResolved < 0 || javaResolved < 0 || exactMatches < 0 || fallbackCount < 0 || holdoutMatches < 0)
            throw new IllegalArgumentException("evaluation counts cannot be negative");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics));
    }
}
