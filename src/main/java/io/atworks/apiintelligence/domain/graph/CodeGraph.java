package io.atworks.apiintelligence.domain.graph;

import io.atworks.apiintelligence.domain.diagnostic.Diagnostic;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record CodeGraph(String apiId, List<CodeNode> nodes, List<CodeEdge> edges,
                        List<Diagnostic> diagnostics) {

    public CodeGraph {
        if (apiId == null || apiId.isBlank()) {
            throw new IllegalArgumentException("apiId is blank");
        }
        nodes = nodes == null ? List.of()
            : nodes.stream().sorted(Comparator.comparing(CodeNode::id)).toList();
        edges = edges == null ? List.of()
            : edges.stream().sorted(Comparator.comparing(CodeEdge::id)).toList();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        Set<String> ids = new HashSet<>();
        for (CodeNode n : nodes) {
            if (!ids.add(n.id())) {
                throw new IllegalArgumentException("duplicate node id");
            }
        }
        for (CodeEdge e : edges) {
            if (!ids.contains(e.sourceNodeId()) || !ids.contains(e.targetNodeId())) {
                throw new IllegalArgumentException("dangling edge");
            }
        }
    }
}
