package io.specscan.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Condition operators. String/collection targets are compared by length/size for the
 * numeric comparison operators (matching bean-validation semantics of @Size).
 */
public enum Operator {
    EQ, NE, GT, GOE, LT, LOE,
    NULL, NOT_NULL,
    EMPTY, NOT_EMPTY,
    BLANK, NOT_BLANK,
    MATCHES, FORMAT,
    IN, NOT_IN,
    EXISTS,
    TRUE, FALSE,
    PAST, PAST_OR_PRESENT, FUTURE, FUTURE_OR_PRESENT,
    /** Raw expression that could not be decomposed into a single comparison. */
    EXPR;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
