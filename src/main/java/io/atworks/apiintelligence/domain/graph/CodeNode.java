package io.atworks.apiintelligence.domain.graph;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record CodeNode(String id, CodeNodeKind kind, String name, SourceLocation sourceLocation,
                       Map<String, String> attributes) {

    public CodeNode {
        id = req(id);
        name = req(name);
        kind = Objects.requireNonNull(kind);
        attributes = attributes == null ? Map.of() : Map.copyOf(new TreeMap<>(attributes));
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("node value is blank");
        }
        return v.trim();
    }
}
