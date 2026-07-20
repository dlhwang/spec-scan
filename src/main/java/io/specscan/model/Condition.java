package io.specscan.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single verifiable condition on request input, response output, or domain state.
 *
 * @param path        JSONPath-style location ($.password, $.items[*].price) or a raw
 *                    expression descriptor for domain conditions
 * @param operator    comparison operator
 * @param expected    expected value (number, string, list for IN, regex for MATCHES); null for
 *                    unary operators such as not_null / not_empty
 * @param location    where the path applies: BODY, QUERY, PATH, HEADER, DOMAIN
 * @param source      how it was discovered: bean-validation, type-constraint, if-throw,
 *                    or-else-throw, assert, if-return, response-construction
 * @param description human-readable context (e.g. the exception message in the code)
 * @param at          file:line of the code that produced this condition
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Condition(
        String path,
        Operator operator,
        Object expected,
        String location,
        String source,
        String description,
        String at) {

    /** Dedup key ignoring provenance. */
    public String key() {
        return path + "|" + operator + "|" + expected + "|" + location;
    }
}
