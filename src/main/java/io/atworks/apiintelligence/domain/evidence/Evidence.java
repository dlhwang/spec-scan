package io.atworks.apiintelligence.domain.evidence;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.util.List;
import java.util.Objects;

public record Evidence(String evidenceId, String apiId, String kind, SourceLocation sourceLocation,
                       String snippet, List<String> graphNodeIds) {

    public Evidence {
        apiId = req(apiId);
        kind = req(kind);
        snippet = req(snippet);
        sourceLocation = Objects.requireNonNull(sourceLocation);
        if (!EvidenceIdGenerator.generate(apiId, sourceLocation, kind).equals(evidenceId)) {
            throw new IllegalArgumentException("evidenceId mismatch");
        }
        graphNodeIds = graphNodeIds == null ? List.of()
            : graphNodeIds.stream().map(Evidence::req).distinct().sorted().toList();
        if (graphNodeIds.isEmpty()) {
            throw new IllegalArgumentException("evidence requires graph node");
        }
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("evidence value is blank");
        }
        return v.trim();
    }
}
