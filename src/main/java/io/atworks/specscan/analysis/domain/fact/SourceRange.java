package io.atworks.specscan.analysis.domain.fact;

import java.util.Objects;

public record SourceRange(String relativePath, int startLine, int startColumn, int endLine, int endColumn) {
    public SourceRange {
        relativePath = Objects.requireNonNull(relativePath, "relativePath").replace('\\', '/');
        if (relativePath.isBlank() || startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1) throw new IllegalArgumentException("invalid source range");
    }
}
