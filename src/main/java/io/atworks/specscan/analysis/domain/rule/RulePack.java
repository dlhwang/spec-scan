package io.atworks.specscan.analysis.domain.rule;

import java.util.List;
import java.util.Objects;

public record RulePack(String id, boolean enabled, List<GraphRule> rules, RulePrecedence precedence) {
    public RulePack {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("rule pack id is required");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        Objects.requireNonNull(precedence, "precedence");
    }
}
