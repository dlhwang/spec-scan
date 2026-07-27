package io.atworks.specscan.analysis.domain.recipe;

public interface SemanticPrimitive {
    PrimitiveDescriptor descriptor();
    PrimitiveOutcome execute(RecipeEvaluationContext context, BindingEnvironment bindings, PrimitiveInvocation invocation);
}
