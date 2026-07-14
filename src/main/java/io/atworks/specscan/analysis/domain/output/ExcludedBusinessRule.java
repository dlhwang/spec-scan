package io.atworks.specscan.analysis.domain.output;

import io.atworks.specscan.analysis.domain.candidate.*;
import java.util.*;

public record ExcludedBusinessRule(String ruleId, BusinessRuleCategory category, ConstraintKind constraintKind,
                                   ExtractionStatus extractionStatus, SemanticStatus semanticStatus,
                                   TargetResolutionStatus targetStatus, String reasonCode, String targetPath,
                                   String operator, List<String> expectedValues, String expectedSource,
                                   double confidence, List<EvidenceRef> evidence) {
    public ExcludedBusinessRule {
        if (ruleId == null || ruleId.isBlank() || reasonCode == null || reasonCode.isBlank())
            throw new IllegalArgumentException("ruleId and reasonCode are required");
        Objects.requireNonNull(category); Objects.requireNonNull(extractionStatus);
        Objects.requireNonNull(semanticStatus); Objects.requireNonNull(targetStatus);
        expectedValues = List.copyOf(Objects.requireNonNull(expectedValues));
        evidence = List.copyOf(Objects.requireNonNull(evidence));
    }
}
