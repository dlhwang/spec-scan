package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;

public record RecipeExecutionResult(String recipeId, String predicateCandidateId, RecipeExecutionStatus status,
                                    BindingEnvironment bindings, List<RecipeDiagnostic> diagnostics, int primitiveInvocations) {
    public RecipeExecutionResult { diagnostics=List.copyOf(diagnostics==null?List.of():diagnostics); }
}
