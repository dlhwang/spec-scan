package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;

public final class RecipeEvaluationPreparationService {
    public RecipeEvaluationRunContext prepare(CompiledRecipePlan plan, EvaluationGraphView evaluationGraph) {
        return new RecipeEvaluationRunContext(plan, evaluationGraph, true);
    }
}
