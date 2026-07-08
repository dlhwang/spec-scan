package io.atworks.specscan.analysis.domain;

public record ParameterRecord(
    long id,
    long apiVersionId,
    String paramType,
    String paramKey,
    String paramValue,
    String variableType,
    String variableKey
) {}
