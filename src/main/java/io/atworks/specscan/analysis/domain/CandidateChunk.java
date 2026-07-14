package io.atworks.specscan.analysis.domain;

import java.util.List;

public record CandidateChunk(
    String chunkId,
    String endpointPath,
    String sourceType,
    List<ValidationCandidate> candidates,
    String operationKey
) {
    public CandidateChunk(String chunkId, String endpointPath, String sourceType,
                          List<ValidationCandidate> candidates) {
        this(chunkId, endpointPath, sourceType, candidates, null);
    }
}
