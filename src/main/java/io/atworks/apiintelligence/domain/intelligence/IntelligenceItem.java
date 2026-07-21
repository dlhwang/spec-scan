package io.atworks.apiintelligence.domain.intelligence;

import java.util.List;

public record IntelligenceItem(String description, String category, String targetLocation,
                               String targetPath, String operator, Object expected,
                               String expectedSource, List<String> evidenceIds,
                               double confidence) {

    public IntelligenceItem(String description, String category, List<String> evidenceIds,
        double confidence) {
        this(description, category, "UNKNOWN", null, "EQ", description, "LLM", evidenceIds,
            confidence);
    }

    public IntelligenceItem {
        description = req(description);
        category = req(category);
        targetLocation = req(targetLocation);
        operator = req(operator);
        expectedSource = req(expectedSource);
        evidenceIds = evidenceIds == null ? List.of()
            : evidenceIds.stream().map(IntelligenceItem::req).distinct().sorted().toList();
        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidence required");
        }
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("invalid confidence");
        }
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("intelligence value is blank");
        }
        return v.trim();
    }
}
