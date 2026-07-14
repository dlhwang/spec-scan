package io.atworks.specscan.analysis.domain.fact;

import java.util.Objects;

public record FactEdge(String id, String sourceNodeId, String targetNodeId, FactEdgeType type, int ordinal, String role) {
    public FactEdge { Objects.requireNonNull(id); Objects.requireNonNull(sourceNodeId); Objects.requireNonNull(targetNodeId); Objects.requireNonNull(type); if (id.isBlank() || sourceNodeId.isBlank() || targetNodeId.isBlank() || ordinal < -1) throw new IllegalArgumentException("invalid fact edge"); }
}
