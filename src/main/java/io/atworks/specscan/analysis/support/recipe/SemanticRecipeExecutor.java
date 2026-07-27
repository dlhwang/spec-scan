package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import java.util.*;

public final class SemanticRecipeExecutor {
    private final DefaultSemanticPrimitiveRegistry registry;
    public SemanticRecipeExecutor() { this(new DefaultSemanticPrimitiveRegistry()); }
    public SemanticRecipeExecutor(DefaultSemanticPrimitiveRegistry registry) { this.registry=registry; }
    public RecipeExecutionResult execute(CompiledRecipePlan plan, SemanticRecipe recipe, RecipeEvaluationContext context) {
        BindingEnvironment bindings = new BindingEnvironment(Map.of("predicate", new RuntimeBinding(BindingType.PREDICATE, context.predicateCandidateId()), "graph", new RuntimeBinding(BindingType.UNKNOWN, context.graph())));
        List<RecipeDiagnostic> diagnostics = new ArrayList<>(); int count=0;
        for (RecipeStep step : recipe.steps()) {
            count++; SemanticPrimitive primitive;
            try { primitive=registry.requireRuntime(step.op()); } catch (RuntimeException exception) { diagnostics.add(new RecipeDiagnostic("runtime", step.op(), "UNKNOWN_PRIMITIVE", step.op())); return new RecipeExecutionResult(recipe.id(), context.predicateCandidateId(), RecipeExecutionStatus.FAILED, bindings, diagnostics, count); }
            PrimitiveOutcome outcome;
            try { outcome=primitive.execute(context, bindings, new PrimitiveInvocation(primitive.descriptor(), step)); } catch (RuntimeException exception) { diagnostics.add(new RecipeDiagnostic("runtime", step.op(), "PRIMITIVE_FAILED", Objects.toString(exception.getMessage(), "execution failure"))); return new RecipeExecutionResult(recipe.id(), context.predicateCandidateId(), RecipeExecutionStatus.FAILED, bindings, diagnostics, count); }
            diagnostics.addAll(outcome.diagnostics()); if (outcome.status()!=RecipeExecutionStatus.RESOLVED) return new RecipeExecutionResult(recipe.id(), context.predicateCandidateId(), outcome.status(), bindings, diagnostics, count);
            try { bindings.put(step.output(), outcome.output()); } catch (RuntimeException exception) { diagnostics.add(new RecipeDiagnostic("runtime", step.op(), "BINDING_TYPE_REASSIGNMENT", exception.getMessage())); return new RecipeExecutionResult(recipe.id(), context.predicateCandidateId(), RecipeExecutionStatus.FAILED, bindings, diagnostics, count); }
        }
        return new RecipeExecutionResult(recipe.id(), context.predicateCandidateId(), RecipeExecutionStatus.RESOLVED, bindings, diagnostics, count);
    }
}
