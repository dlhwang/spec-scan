package io.atworks.specscan.analysis.domain.semantic;

import java.util.List;
import java.util.Objects;

public record SemanticExpression(String nodeId, String operator, List<SemanticExpression> operands) {
    public SemanticExpression {
        Objects.requireNonNull(nodeId, "nodeId");
        operands = List.copyOf(Objects.requireNonNull(operands, "operands"));
        if (nodeId.isBlank()) throw new IllegalArgumentException("nodeId is required");
    }
}
