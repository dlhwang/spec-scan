package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ApiCondition(
    String targetPath,
    String operator,
    String expected,
    String evidence,
    double confidence,
    String llmReason,
    SourceTrace sourceTrace
) {}
