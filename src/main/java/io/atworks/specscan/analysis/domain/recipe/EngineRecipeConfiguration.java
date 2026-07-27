package io.atworks.specscan.analysis.domain.recipe;

public record EngineRecipeConfiguration(int maxDepth, int maxVisitedMethodsPerApi, int maxEdgesPerApi,
                                        boolean deterministicOrder, boolean reportUnclassified,
                                        boolean reportTruncation) {
    public static final int DEFAULT_MAX_DEPTH = 15;
    public static final int DEFAULT_MAX_VISITED_METHODS = 500;
    public static final int DEFAULT_MAX_EDGES = 10_000;

    public EngineRecipeConfiguration {
        if (maxDepth <= 0 || maxVisitedMethodsPerApi <= 0 || maxEdgesPerApi <= 0)
            throw new IllegalArgumentException("traversal budgets must be positive");
    }

    public static EngineRecipeConfiguration defaults() {
        return new EngineRecipeConfiguration(DEFAULT_MAX_DEPTH, DEFAULT_MAX_VISITED_METHODS,
            DEFAULT_MAX_EDGES, true, true, true);
    }
}
