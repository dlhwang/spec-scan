package io.atworks.specscan.analysis.domain.recipe;

import java.util.*;

public final class RuntimeBudget {
    private final int maxDepth, maxVisitedMethods, maxEdges;
    private int depth, visitedMethods, edges;
    private final Set<String> visited = new HashSet<>();
    public RuntimeBudget(EngineRecipeConfiguration configuration) { maxDepth=configuration.maxDepth(); maxVisitedMethods=configuration.maxVisitedMethodsPerApi(); maxEdges=configuration.maxEdgesPerApi(); }
    public boolean enter(String nodeId) { if (nodeId == null || visited.contains(nodeId)) return false; if (depth >= maxDepth || visitedMethods >= maxVisitedMethods) return false; visited.add(nodeId); visitedMethods++; depth++; return true; }
    public void leave() { if (depth > 0) depth--; }
    public boolean edge() { if (edges >= maxEdges) return false; edges++; return true; }
    public boolean cycle(String nodeId) { return visited.contains(nodeId); }
    public int visitedMethods() { return visitedMethods; }
    public int edges() { return edges; }
}
