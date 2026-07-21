package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.fact.*;
import java.util.*;

public final class FactGraphIndex {
    private final FactCodeGraph graph;
    private final Map<String, FactNode> nodes;
    private final Map<String, List<FactEdge>> outgoing;
    private final Map<String, List<FactEdge>> incoming;

    public FactGraphIndex(FactCodeGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        Map<String, FactNode> nodeIndex = new HashMap<>();
        graph.nodes().forEach(node -> nodeIndex.put(node.id(), node));
        nodes = Map.copyOf(nodeIndex);
        outgoing = edgeIndex(graph.edges(), true);
        incoming = edgeIndex(graph.edges(), false);
    }

    public FactCodeGraph graph() { return graph; }
    public FactNode node(String id) { return nodes.get(id); }
    public List<FactEdge> outgoing(String nodeId) { return outgoing.getOrDefault(nodeId, List.of()); }
    public List<FactEdge> incoming(String nodeId) { return incoming.getOrDefault(nodeId, List.of()); }

    public List<FactNode> targets(String sourceId, FactEdgeType type) {
        return outgoing(sourceId).stream().filter(edge -> edge.type() == type)
            .sorted(Comparator.comparingInt(FactEdge::ordinal).thenComparing(FactEdge::targetNodeId))
            .map(edge -> node(edge.targetNodeId())).filter(Objects::nonNull).toList();
    }

    public List<FactNode> targets(String sourceId, FactEdgeType type, String role) {
        return outgoing(sourceId).stream().filter(edge -> edge.type() == type
                && Objects.equals(role, edge.role()))
            .sorted(Comparator.comparingInt(FactEdge::ordinal).thenComparing(FactEdge::targetNodeId))
            .map(edge -> node(edge.targetNodeId())).filter(Objects::nonNull).toList();
    }

    private Map<String, List<FactEdge>> edgeIndex(List<FactEdge> edges, boolean bySource) {
        Map<String, List<FactEdge>> result = new HashMap<>();
        for (FactEdge edge : edges) {
            String key = bySource ? edge.sourceNodeId() : edge.targetNodeId();
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(edge);
        }
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }
}
