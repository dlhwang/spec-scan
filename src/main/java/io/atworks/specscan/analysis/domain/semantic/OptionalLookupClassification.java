package io.atworks.specscan.analysis.domain.semantic;

import java.util.Objects;

public record OptionalLookupClassification(OptionalLookupKind kind,
                                           SemanticConstraintMatch semanticMatch,
                                           String terminalCallNodeId,
                                           String lookupCallNodeId) {
    public OptionalLookupClassification {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(semanticMatch, "semanticMatch");
        Objects.requireNonNull(terminalCallNodeId, "terminalCallNodeId");
        if (terminalCallNodeId.isBlank()) throw new IllegalArgumentException("terminalCallNodeId is required");
    }
}
