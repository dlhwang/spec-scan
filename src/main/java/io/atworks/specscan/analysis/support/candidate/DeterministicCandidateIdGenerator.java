package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.SemanticStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DeterministicCandidateIdGenerator {
    public String forPredicate(String graphId, String conditionNodeId) {
        return "PREDICATE:" + hash(join("PREDICATE", required(graphId), required(conditionNodeId))).substring(0, 24);
    }

    public String forBusinessRule(String predicateCandidateId, String ruleId, SemanticStatus status) {
        String discriminator = ruleId == null || ruleId.isBlank() ? required(status).name() : ruleId.trim();
        return "BUSINESS_RULE:" + hash(join("BUSINESS_RULE", required(predicateCandidateId), discriminator)).substring(0, 24);
    }

    public String forBusinessRule(String predicateCandidateId, String ruleId, SemanticStatus status,
                                  String evidenceFingerprint) {
        String discriminator = ruleId == null || ruleId.isBlank() ? required(status).name() : ruleId.trim();
        return "BUSINESS_RULE:" + hash(join("BUSINESS_RULE", required(predicateCandidateId), discriminator,
            required(evidenceFingerprint))).substring(0, 24);
    }

    private String join(String... values) { return String.join("|", values); }
    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("identity value is required");
        return value.replace('\\', '/');
    }
    private <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("identity value is required");
        return value;
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
