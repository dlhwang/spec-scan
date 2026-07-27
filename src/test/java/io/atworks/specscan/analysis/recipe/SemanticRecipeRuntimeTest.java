package io.atworks.specscan.analysis.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import io.atworks.specscan.analysis.support.recipe.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SemanticRecipeRuntimeTest {
    @TempDir Path temporary;

    @Test void executesTypedRecipeAndKeepsOrderingDeterministic() throws Exception {
        RecipeSourceSet sources = new RecipeSourceSet(
            Path.of("src/test/resources/recipe-u02/engine-config.yaml"),
            Path.of("src/test/resources/recipe-u02/semantic-recipes.yaml"),
            Path.of("src/test/resources/recipe-u02/framework-catalog.yaml"));
        CompiledRecipePlan plan = new RecipePlanBootstrapService().bootstrap(sources).plan();
        SemanticRecipe recipe = plan.recipes().get(0);
        EvaluationGraphView graph = name -> Optional.of("runtime-" + name);
        RecipeEvaluationContext context = new RecipeEvaluationContext("repo", "GET /x", "p-1", graph, new RuntimeBudget(plan.engine()));
        RecipeExecutionResult result = new SemanticRecipeExecutor().execute(plan, recipe, context);
        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.RESOLVED);
        assertThat(result.primitiveInvocations()).isEqualTo(6);
        assertThat(result.bindings().snapshot()).containsKeys("failure", "comparison", "target", "expected", "operator", "constraint");
    }

    @Test void unprovenGraphBindingIsUnresolvedWithoutSynthesizingValue() throws Exception {
        RecipeSourceSet sources = new RecipeSourceSet(Path.of("src/test/resources/recipe-u02/engine-config.yaml"), Path.of("src/test/resources/recipe-u02/semantic-recipes.yaml"), Path.of("src/test/resources/recipe-u02/framework-catalog.yaml"));
        CompiledRecipePlan plan = new RecipePlanBootstrapService().bootstrap(sources).plan();
        EvaluationGraphView empty = name -> Optional.empty();
        RecipeExecutionResult result = new SemanticRecipeExecutor().execute(plan, plan.recipes().get(0), new RecipeEvaluationContext("repo", "GET /x", "p-1", empty, new RuntimeBudget(plan.engine())));
        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.UNRESOLVED);
        assertThat(result.bindings().get("target")).isEmpty();
    }

    @Test void bindingTypeCannotBeReassignedAndBudgetGuardsCycleAndEdges() {
        BindingEnvironment bindings = new BindingEnvironment(Map.of("x", new RuntimeBinding(BindingType.TARGET, "x")));
        assertThatThrownBy(() -> bindings.put("x", new RuntimeBinding(BindingType.LITERAL, "x")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BINDING_TYPE_REASSIGNMENT");
        RuntimeBudget budget = new RuntimeBudget(new EngineRecipeConfiguration(1, 1, 1, true, true, true));
        assertThat(budget.enter("a")).isTrue();
        assertThat(budget.cycle("a")).isTrue();
        budget.leave();
        assertThat(budget.enter("b")).isFalse();
        assertThat(budget.edge()).isTrue();
        assertThat(budget.edge()).isFalse();
    }
}
