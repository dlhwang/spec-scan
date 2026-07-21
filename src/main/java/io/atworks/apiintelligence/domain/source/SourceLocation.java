package io.atworks.apiintelligence.domain.source;

import java.util.Objects;

public record SourceLocation(String relativePath, int startLine, int startColumn, int endLine,
                             int endColumn) {

    public SourceLocation {
        relativePath = normalizePath(relativePath);
        if (startLine < 1 || endLine < 1 || startColumn < 1 || endColumn < 1) {
            throw new IllegalArgumentException("source positions are one-based");
        }
        if (endLine < startLine || (endLine == startLine && endColumn < startColumn)) {
            throw new IllegalArgumentException("source range is reversed");
        }
    }

    public static String normalizePath(String value) {
        String p = Objects.requireNonNull(value).trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (p.isBlank() || p.startsWith("/") || p.contains("../")) {
            throw new IllegalArgumentException("source path must be relative");
        }
        return p;
    }
}
