package io.atworks.specscan.analysis.domain.semantic;

public record SemanticTargetResolution(SemanticTargetRole role, String path, String evidenceNodeId) {
    public SemanticTargetResolution {
        if (role == null) throw new IllegalArgumentException("role is required");
        if (evidenceNodeId == null || evidenceNodeId.isBlank()) {
            throw new IllegalArgumentException("evidenceNodeId is required");
        }
    }
}
