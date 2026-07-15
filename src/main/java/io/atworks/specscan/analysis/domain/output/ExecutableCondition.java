package io.atworks.specscan.analysis.domain.output;

import io.atworks.specscan.analysis.domain.candidate.EvidenceRef;
import java.util.*;

public record ExecutableCondition(String targetLocation, String targetPath, String operator,
                                  List<String> expectedValues, String expectedSource, String ruleId,
                                  double confidence, List<EvidenceRef> evidence) {
    public ExecutableCondition {
        requireText(targetLocation, "targetLocation"); requireText(targetPath, "targetPath");
        operator = CanonicalOperator.parse(operator).name(); requireText(ruleId, "ruleId");
        expectedValues = List.copyOf(Objects.requireNonNull(expectedValues, "expectedValues"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (CanonicalOperator.parse(operator).requiresExpectedValue() && expectedValues.isEmpty())
            throw new IllegalArgumentException("expectedValues are required for " + operator);
        if (evidence.isEmpty()) throw new IllegalArgumentException("evidence is required");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("invalid confidence");
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
