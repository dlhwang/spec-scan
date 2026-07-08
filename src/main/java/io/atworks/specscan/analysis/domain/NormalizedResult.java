package io.atworks.specscan.analysis.domain;

import java.util.List;

public record NormalizedResult(
    List<ApiCondition> conditions,
    List<ValidationCandidate> rejected,
    List<CandidateChunk> invalidChunks
) {}
