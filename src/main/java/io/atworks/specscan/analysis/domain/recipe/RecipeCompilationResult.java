package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;

public record RecipeCompilationResult(CompiledRecipePlan plan, List<RecipeDiagnostic> diagnostics) {
    public RecipeCompilationResult { diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics); }
    public boolean successful() { return plan != null && diagnostics.isEmpty(); }
}
