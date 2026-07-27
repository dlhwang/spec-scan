package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;

public record RecipeEvaluationRunContext(CompiledRecipePlan plan, EvaluationGraphView graph,
                                         boolean isolatedFromNormalGraph) {
    public RecipeEvaluationRunContext { if (plan == null || graph == null) throw new IllegalArgumentException("evaluation context inputs required"); }
}
