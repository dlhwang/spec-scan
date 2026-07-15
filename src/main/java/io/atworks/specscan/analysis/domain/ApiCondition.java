package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ApiCondition(
    ConditionType conditionType,
    ConditionLocation targetLocation,
    String targetPath,
    String operator,
    String expected,
    String evidence,
    double confidence,
    String llmReason,
    SourceTrace sourceTrace,
    String endpointPath
) {
    // Convenience constructor for request-side conditions.
    public ApiCondition(
        ConditionLocation targetLocation,
        String targetPath,
        String operator,
        String expected,
        String evidence,
        double confidence,
        String llmReason,
        SourceTrace sourceTrace,
        String endpointPath
    ) {
        this(
            ConditionType.PRECONDITION,
            targetLocation,
            targetPath,
            operator,
            expected,
            evidence,
            confidence,
            llmReason,
            sourceTrace,
            endpointPath
        );
    }
}

