package io.atworks.specscan.analysis.domain;

public record ApiValueValidationRecord(
    long id,
    long apiId,
    int version,
    int orderNo,
    String jsonPath,
    String condition,
    String value,
    boolean isActive,
    Long parentId,
    String logicalOperator
) {}
