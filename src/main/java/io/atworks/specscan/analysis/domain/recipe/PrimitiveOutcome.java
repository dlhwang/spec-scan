package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;

public record PrimitiveOutcome(RecipeExecutionStatus status, RuntimeBinding output, List<RecipeDiagnostic> diagnostics) {
    public PrimitiveOutcome { diagnostics=List.copyOf(diagnostics==null?List.of():diagnostics); }
    public static PrimitiveOutcome unresolved(String code, String message) { return new PrimitiveOutcome(RecipeExecutionStatus.UNRESOLVED, null, List.of(new RecipeDiagnostic("runtime", null, code, message))); }
    public static PrimitiveOutcome partial(String code, String message) { return new PrimitiveOutcome(RecipeExecutionStatus.PARTIAL_ANALYSIS, null, List.of(new RecipeDiagnostic("runtime", null, code, message))); }
}
