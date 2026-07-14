package io.atworks.specscan.analysis.domain.fact;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record FactCodeGraph(String graphId, String apiMethodNodeId, List<FactNode> nodes, List<FactEdge> edges) {
    public FactCodeGraph { nodes = List.copyOf(nodes); edges = List.copyOf(edges); Set<String> ids = new HashSet<>(); for (FactNode n : nodes) if (!ids.add(n.id())) throw new IllegalArgumentException("duplicate node id"); if (!ids.contains(apiMethodNodeId)) throw new IllegalArgumentException("missing api root"); for (FactEdge e : edges) if (!ids.contains(e.sourceNodeId()) || !ids.contains(e.targetNodeId())) throw new IllegalArgumentException("dangling edge"); }
}
