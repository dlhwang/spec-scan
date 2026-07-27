package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;
import java.util.Objects;

public record CompiledRecipePlan(RecipePackVersion version, EngineRecipeConfiguration engine,
                                 List<SemanticRecipe> recipes, List<FrameworkCatalogEntry> frameworkCatalog,
                                 String contentDigest) {
    public CompiledRecipePlan {
        Objects.requireNonNull(version, "version"); Objects.requireNonNull(engine, "engine");
        recipes = List.copyOf(Objects.requireNonNull(recipes, "recipes"));
        frameworkCatalog = List.copyOf(Objects.requireNonNull(frameworkCatalog, "frameworkCatalog"));
        if (contentDigest == null || !contentDigest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("contentDigest must be lowercase SHA-256");
    }
}
