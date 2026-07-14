package io.atworks.specscan.analysis.domain.rule;

import java.util.Objects;
import java.util.Set;

public record MethodScope(String graphId, Set<String> methodNodeIds) {
    public MethodScope {
        if (graphId == null || graphId.isBlank()) throw new IllegalArgumentException("graphId is required");
        methodNodeIds = Set.copyOf(Objects.requireNonNull(methodNodeIds, "methodNodeIds"));
        if (methodNodeIds.isEmpty() || methodNodeIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("method scope requires method node ids");
        }
    }
}
