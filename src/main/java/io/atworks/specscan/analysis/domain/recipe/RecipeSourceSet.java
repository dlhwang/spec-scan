package io.atworks.specscan.analysis.domain.recipe;

import java.nio.file.Path;
import java.util.Objects;

public record RecipeSourceSet(Path engineConfig, Path semanticRecipes, Path frameworkCatalog) {
    public RecipeSourceSet {
        Objects.requireNonNull(engineConfig, "engineConfig");
        Objects.requireNonNull(semanticRecipes, "semanticRecipes");
        Objects.requireNonNull(frameworkCatalog, "frameworkCatalog");
    }
}
