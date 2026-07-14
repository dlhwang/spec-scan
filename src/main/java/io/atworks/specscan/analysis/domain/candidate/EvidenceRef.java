package io.atworks.specscan.analysis.domain.candidate;

import java.util.Objects;

public record EvidenceRef(String nodeId, String filePath, int startLine, int startColumn,
                          int endLine, int endColumn, EvidenceRole role, String snippet) {
    public EvidenceRef {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(role, "role");
        if (nodeId.isBlank() || filePath.isBlank() || startLine < 1 || startColumn < 1
            || endLine < startLine || endColumn < 1 || (endLine == startLine && endColumn < startColumn)) {
            throw new IllegalArgumentException("invalid evidence reference");
        }
        filePath = filePath.replace('\\', '/');
    }
}
