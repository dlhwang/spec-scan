package io.atworks.specscan.analysis.support.legacy;

import io.atworks.specscan.analysis.domain.ConditionLocation;
import java.util.Set;

/** Compatibility-only classification retained until the Unit 06 quality gate. */
public final class LegacyConditionExclusionPolicy {
    private static final Set<String> NON_EXECUTABLE_OPERATORS = Set.of(
        "EXISTS_IN_REPOSITORY", "OPTIMISTIC_LOCK_MATCH", "HAS_CANCELLATION_PERMISSION", "STATE_IN");

    public boolean excludes(String targetPath, ConditionLocation location, String operator) {
        if (location == ConditionLocation.AUTH || location == ConditionLocation.RESOURCE) return true;
        if (operator != null && NON_EXECUTABLE_OPERATORS.contains(operator)) return true;
        if (targetPath == null) return false;
        String normalized = targetPath.toLowerCase();
        return normalized.contains("currentuser") || normalized.contains("order.state")
            || normalized.contains("orderstate");
    }
}
