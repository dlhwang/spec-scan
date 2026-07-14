package io.atworks.specscan.analysis.domain.fact;

public record FactGraphTraversalStats(int maxObservedDepth, int visitedMethods, int edges) { public FactGraphTraversalStats { if (maxObservedDepth < 0 || visitedMethods < 0 || edges < 0) throw new IllegalArgumentException("negative stats"); } }
