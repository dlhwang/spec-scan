package io.atworks.specscan.analysis.support.fact;

import io.atworks.specscan.analysis.domain.fact.*;
import java.util.HashSet;
import java.util.Set;

public final class FactGraphIntegrityValidator {
    public void validate(FactCodeGraph graph, FactGraphTraversalBudget budget, FactGraphTraversalStats stats) {
        Set<String> ids = new HashSet<>();
        for (FactNode node : graph.nodes()) if (!ids.add(node.id())) throw new IllegalArgumentException("duplicate node id");
        for (FactEdge edge : graph.edges()) if (!ids.contains(edge.sourceNodeId()) || !ids.contains(edge.targetNodeId())) throw new IllegalArgumentException("dangling edge");
        if (stats.maxObservedDepth() > budget.maxDepth() || stats.visitedMethods() > budget.maxVisitedMethodsPerApi() || stats.edges() > budget.maxEdgesPerApi()) throw new IllegalArgumentException("budget exceeded");
    }
}
