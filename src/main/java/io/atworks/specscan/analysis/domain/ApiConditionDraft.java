package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ApiConditionDraft(
    String targetPath,
    String operator,
    String expected,
    String evidence,
    SourceTrace sourceTrace
) {}
