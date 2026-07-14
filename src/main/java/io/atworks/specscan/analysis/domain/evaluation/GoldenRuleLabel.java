package io.atworks.specscan.analysis.domain.evaluation;

import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCategory;
import java.util.*;

public record GoldenRuleLabel(String id, String dataset, boolean expectedCandidate, boolean supportedScope,
                              Set<String> acceptedRuleIds, Set<BusinessRuleCategory> acceptedCategories,
                              boolean targetRequired) {
    public GoldenRuleLabel {
        requireText(id, "id"); requireText(dataset, "dataset");
        acceptedRuleIds = Set.copyOf(Objects.requireNonNull(acceptedRuleIds));
        acceptedCategories = Set.copyOf(Objects.requireNonNull(acceptedCategories));
        if (!expectedCandidate && (!acceptedRuleIds.isEmpty() || !acceptedCategories.isEmpty()))
            throw new IllegalArgumentException("negative label cannot accept semantic results");
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
