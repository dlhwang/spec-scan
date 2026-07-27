package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;

public record RecipeLoadResult(RecipeSourceSet sources, List<RecipeDiagnostic> diagnostics) {
    public RecipeLoadResult { diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics); }
    public boolean valid() { return diagnostics.isEmpty(); }
}
