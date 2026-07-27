package io.atworks.specscan.analysis.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import io.atworks.specscan.analysis.support.recipe.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CoreSemanticRecipeTest {
    private CompiledRecipePlan plan() throws Exception {
        return new RecipePlanBootstrapService().bootstrap(new RecipeSourceSet(
            Path.of("src/test/resources/recipe-u04/engine-config.yaml"),
            Path.of("src/test/resources/recipe-u04/semantic-recipes.yaml"),
            Path.of("src/test/resources/recipe-u04/framework-catalog.yaml"))).plan();
    }

    @Test void compilesFourGenericSpringMvcClassifierFamilies() throws Exception {
        CompiledRecipePlan plan = plan();
        assertThat(plan.recipes()).extracting(SemanticRecipe::id)
            .containsExactly("JAVA_BINARY_FAILURE_GUARD", "JAVA_COMPOSITE_REQUIREMENT", "JAVA_NULL_EMPTY_GUARD", "JDK_OPTIONAL_LOOKUP_FAILURE");
    }

    @Test void sameRecipesResolveAgainstRenamedGraphBindings() throws Exception {
        CompiledRecipePlan plan = plan();
        SemanticRecipeExecutor executor = new SemanticRecipeExecutor();
        for (String candidate : List.of("OrderController.check", "AccountController.validate")) {
            EvaluationGraphView graph = name -> Optional.of(candidate + ":" + name);
            for (SemanticRecipe recipe : plan.recipes()) {
                RecipeExecutionResult result = executor.execute(plan, recipe,
                    new RecipeEvaluationContext("repo", "GET /resource", candidate, graph, new RuntimeBudget(plan.engine())));
                assertThat(result.status()).as(recipe.id()).isEqualTo(RecipeExecutionStatus.RESOLVED);
                assertThat(result.bindings().get("constraint")).isPresent();
            }
        }
    }

    @Test void missingGraphEvidenceRemainsUnresolved() throws Exception {
        CompiledRecipePlan plan = plan();
        RecipeExecutionResult result = new SemanticRecipeExecutor().execute(plan, plan.recipes().get(1),
            new RecipeEvaluationContext("repo", "GET /resource", "candidate", name -> Optional.empty(), new RuntimeBudget(plan.engine())));
        assertThat(result.status()).isEqualTo(RecipeExecutionStatus.UNRESOLVED);
        assertThat(result.bindings().get("constraint")).isEmpty();
    }
}
