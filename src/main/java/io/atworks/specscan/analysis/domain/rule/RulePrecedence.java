package io.atworks.specscan.analysis.domain.rule;

import java.util.Map;
import java.util.Objects;

public record RulePrecedence(Map<String, Integer> tiers) {
    public static final int DEFAULT_TIER = 0;
    public RulePrecedence { tiers = Map.copyOf(Objects.requireNonNull(tiers, "tiers")); }
    public int tier(String ruleId) { return tiers.getOrDefault(ruleId, DEFAULT_TIER); }
    public static RulePrecedence none() { return new RulePrecedence(Map.of()); }
}
