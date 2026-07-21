package io.atworks.apiintelligence.domain.evidence;

import io.atworks.apiintelligence.domain.api.ApiIdGenerator;
import io.atworks.apiintelligence.domain.source.SourceLocation;

public final class EvidenceIdGenerator {

    private EvidenceIdGenerator() {
    }

    public static String generate(String apiId, SourceLocation l, String kind) {
        if (apiId == null || apiId.isBlank() || kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("evidence identity is blank");
        }
        return ApiIdGenerator.sha256(
            apiId.trim() + "\n" + l.relativePath() + "\n" + l.startLine() + "\n" + l.endLine()
                + "\n" + kind.trim());
    }
}
