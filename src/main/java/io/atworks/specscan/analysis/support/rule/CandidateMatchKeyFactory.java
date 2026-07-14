package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import java.util.stream.Collectors;

public final class CandidateMatchKeyFactory {
    public CandidateMatchKey key(BusinessRuleCandidate candidate) {
        return new CandidateMatchKey(candidate.predicateCandidateId(), candidate.ruleId(), fingerprint(candidate));
    }
    public String fingerprint(BusinessRuleCandidate candidate) {
        return candidate.evidence().stream().map(e -> e.nodeId() + ":" + e.role().name()).distinct().sorted()
            .collect(Collectors.joining("|"));
    }
    public record CandidateMatchKey(String predicateCandidateId, String ruleId, String evidenceFingerprint) {}
}
