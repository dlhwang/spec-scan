package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;
import java.util.Objects;

public record SemanticRecipe(String id, String family, List<RecipeStep> steps) {
    public SemanticRecipe {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("recipe id is required");
        if (family == null || family.isBlank()) throw new IllegalArgumentException("recipe family is required");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty()) throw new IllegalArgumentException("recipe steps are required");
    }
}
