package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ValidationCandidate(
    String candidateId,
    String sourceType,
    String targetPath,
    String evidenceSnippet,
    double confidence,
    SourceTrace sourceTrace,
    String operationKey
) {
    public ValidationCandidate(String candidateId, String sourceType, String targetPath,
                               String evidenceSnippet, double confidence, SourceTrace sourceTrace) {
        this(candidateId, sourceType, targetPath, evidenceSnippet, confidence, sourceTrace, null);
    }

    public ValidationCandidate withOperationKey(String value) {
        return new ValidationCandidate(candidateId, sourceType, targetPath, evidenceSnippet,
            confidence, sourceTrace, value);
    }
}
