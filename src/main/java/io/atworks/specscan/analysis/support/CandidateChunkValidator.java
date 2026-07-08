package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.ValidationCandidate;

import java.util.HashSet;
import java.util.Set;

public class CandidateChunkValidator {

    /**
     * CandidateChunk의 무결성을 검증합니다.
     */
    public boolean isValid(CandidateChunk chunk) {
        if (chunk.candidates() == null || chunk.candidates().isEmpty()) {
            return false;
        }

        Set<String> idSet = new HashSet<>();
        for (ValidationCandidate candidate : chunk.candidates()) {
            // 1. ID 누락 및 중복 검사
            if (candidate.candidateId() == null || candidate.candidateId().isEmpty()) {
                return false;
            }
            if (idSet.contains(candidate.candidateId())) {
                return false; // 중복 발생
            }
            idSet.add(candidate.candidateId());

            // 2. evidenceSnippet 검사
            if (candidate.evidenceSnippet() == null || candidate.evidenceSnippet().trim().isEmpty()) {
                return false;
            }

            // 3. sourceTrace 검사
            if (candidate.sourceTrace() == null || candidate.sourceTrace().fileRelativePath() == null) {
                return false;
            }
        }

        return true;
    }
}
