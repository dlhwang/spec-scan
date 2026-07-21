package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCandidate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SemanticDispatchResult(List<BusinessRuleCandidate> candidates,
                                     Set<String> suppressedLegacyRuleIds) {
    public SemanticDispatchResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        suppressedLegacyRuleIds = Set.copyOf(Objects.requireNonNull(
            suppressedLegacyRuleIds, "suppressedLegacyRuleIds"));
    }

    public static SemanticDispatchResult empty() {
        return new SemanticDispatchResult(List.of(), Set.of());
    }
}
