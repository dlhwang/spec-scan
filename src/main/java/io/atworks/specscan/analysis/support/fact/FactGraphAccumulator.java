package io.atworks.specscan.analysis.support.fact;

import io.atworks.specscan.analysis.domain.fact.*;
import java.util.*;

public final class FactGraphAccumulator {
    private final FactGraphTraversalBudget budget;
    private final Map<String, FactNode> nodes = new LinkedHashMap<>();
    private final Map<String, FactEdge> edges = new LinkedHashMap<>();
    private boolean edgeLimitReached;
    public FactGraphAccumulator(FactGraphTraversalBudget budget) { this.budget = Objects.requireNonNull(budget); }
    public void addNode(FactNode node) {
        FactNode previous = nodes.putIfAbsent(node.id(), node);
        if (previous == null || previous.equals(node)) return;
        if (sameSourceFact(previous, node)) {
            if (node.typeResolution().status() == TypeResolutionStatus.RESOLVED
                    && previous.typeResolution().status() != TypeResolutionStatus.RESOLVED) nodes.put(node.id(), node);
            else if (previous.typeResolution().status() == TypeResolutionStatus.RESOLVED
                    && node.typeResolution().status() != TypeResolutionStatus.RESOLVED) return;
            else throw new IllegalStateException("NODE_RESOLUTION_CONFLICT:" + node.id());
            return;
        }
        throw new IllegalStateException("NODE_ID_COLLISION:" + node.id());
    }
    private boolean sameSourceFact(FactNode left, FactNode right) {
        return left.type() == right.type() && left.sourceRange().equals(right.sourceRange())
            && Objects.equals(left.snippet(), right.snippet()) && left.payload().equals(right.payload());
    }
    public boolean addRelation(FactNode source, FactNode target, FactEdge edge) { if (edges.size() >= budget.maxEdgesPerApi() && !edges.containsKey(edge.id())) { edgeLimitReached = true; return false; } addNode(source); addNode(target); edges.putIfAbsent(edge.id(), edge); return true; }
    public int edgeCount() { return edges.size(); }
    public boolean edgeLimitReached() { return edgeLimitReached; }
    public List<FactNode> nodes() { return List.copyOf(nodes.values()); }
    public List<FactEdge> edges() { return List.copyOf(edges.values()); }
    public FactCodeGraph snapshot(String graphId, String rootId) { return new FactCodeGraph(graphId, rootId, nodes(), edges()); }
}
