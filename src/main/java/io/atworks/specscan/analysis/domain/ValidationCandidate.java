package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ValidationCandidate(
    String candidateId,
    String sourceType,
    String targetPath,
    String evidenceSnippet,
    double confidence,
    SourceTrace sourceTrace
) {}
