package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;

public final class RecipePlanBootstrapService {
    private final YamlRecipeSourceLoader loader;
    private final SemanticRecipeCompiler compiler;
    private final SemanticPrimitiveRegistry registry;
    public RecipePlanBootstrapService() { this(new YamlRecipeSourceLoader(), new SemanticRecipeCompiler(), new DefaultPrimitiveDescriptorRegistry()); }
    public RecipePlanBootstrapService(YamlRecipeSourceLoader loader, SemanticRecipeCompiler compiler, SemanticPrimitiveRegistry registry) { this.loader=loader;this.compiler=compiler;this.registry=registry; }
    public RecipeCompilationResult bootstrap(RecipeSourceSet sources) { var loaded=loader.load(sources); return compiler.compile(loaded, registry); }
}
