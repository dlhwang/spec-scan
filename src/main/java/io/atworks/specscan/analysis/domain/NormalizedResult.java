package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.IngestionWarning;

import java.util.List;

public record NormalizedResult(
    List<ApiCondition> conditions,
    List<ValidationCandidate> rejected,
    List<CandidateChunk> invalidChunks,
    List<IngestionWarning> warnings
) {}
