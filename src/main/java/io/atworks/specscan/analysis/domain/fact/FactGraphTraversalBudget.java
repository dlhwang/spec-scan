package io.atworks.specscan.analysis.domain.fact;

public record FactGraphTraversalBudget(int maxDepth, int maxVisitedMethodsPerApi, int maxEdgesPerApi) {
    public FactGraphTraversalBudget { if (maxDepth < 1 || maxVisitedMethodsPerApi < 1 || maxEdgesPerApi < 1) throw new IllegalArgumentException("budget values must be positive"); }
    public static FactGraphTraversalBudget defaults() { return new FactGraphTraversalBudget(15, 500, 10_000); }
}
