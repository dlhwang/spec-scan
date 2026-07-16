package io.atworks.specscan.analysis.domain.output;

import java.util.Map;
import java.util.Set;

public enum CanonicalOperator {
    EQ, NEQ, GT, GTE, LT, LTE, CONTAINS, NOT_CONTAINS, EMPTY, NOT_EMPTY, NULL, NOT_NULL, SIZE, NOT_BLANK, PATTERN, EMAIL, MIN_AGE;

    private static final Map<String, CanonicalOperator> ALIASES = Map.ofEntries(
        Map.entry("EQUALS", EQ), Map.entry("NOT_EQUALS", NEQ),
        Map.entry("GREATER_THAN", GT), Map.entry("GREATER_THAN_OR_EQUAL", GTE),
        Map.entry("LESS_THAN", LT), Map.entry("LESS_THAN_OR_EQUAL", LTE),
        Map.entry("IS_NULL", NULL), Map.entry("IS_NOT_NULL", NOT_NULL)
    );
    private static final Set<CanonicalOperator> UNARY = Set.of(EMPTY, NOT_EMPTY, NULL, NOT_NULL, NOT_BLANK, EMAIL);

    public static CanonicalOperator parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("operator is required");
        try { return valueOf(value); }
        catch (IllegalArgumentException ignored) {
            CanonicalOperator alias = ALIASES.get(value);
            if (alias != null) return alias;
            throw new IllegalArgumentException("unsupported operator: " + value);
        }
    }

    public boolean requiresExpectedValue() { return !UNARY.contains(this); }
}
