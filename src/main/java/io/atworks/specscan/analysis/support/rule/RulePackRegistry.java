package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class RulePackRegistry {
    private final List<GraphRule> rules;
    private final Map<String, Integer> tiers;

    public RulePackRegistry(List<RulePack> packs) {
        Map<String, GraphRule> unique = new HashMap<>();
        Map<String, Integer> collectedTiers = new HashMap<>();
        for (RulePack pack : List.copyOf(packs)) if (pack.enabled()) {
            for (GraphRule rule : pack.rules()) {
                if (rule == null || rule.id() == null || rule.id().isBlank() || rule.layer() == null) {
                    throw new IllegalArgumentException("invalid graph rule");
                }
                if (unique.putIfAbsent(rule.id(), rule) != null) throw new IllegalArgumentException("DUPLICATE_RULE_ID: " + rule.id());
                collectedTiers.put(rule.id(), pack.precedence().tier(rule.id()));
            }
        }
        this.rules = unique.values().stream().sorted(Comparator.comparing(GraphRule::id)).toList();
        this.tiers = Map.copyOf(collectedTiers);
    }
    public List<GraphRule> rules() { return rules; }
    public int tier(String ruleId) { return tiers.getOrDefault(ruleId, RulePrecedence.DEFAULT_TIER); }
}
