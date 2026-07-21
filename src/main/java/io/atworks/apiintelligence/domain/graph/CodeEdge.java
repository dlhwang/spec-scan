package io.atworks.apiintelligence.domain.graph;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.util.Objects;

public record CodeEdge(String id, CodeEdgeKind kind, String sourceNodeId, String targetNodeId,
                       SourceLocation sourceLocation) {

    public CodeEdge {
        id = req(id);
        sourceNodeId = req(sourceNodeId);
        targetNodeId = req(targetNodeId);
        kind = Objects.requireNonNull(kind);
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("edge value is blank");
        }
        return v.trim();
    }
}
