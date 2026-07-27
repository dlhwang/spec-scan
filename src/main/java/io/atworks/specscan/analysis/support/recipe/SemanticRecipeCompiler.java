package io.atworks.specscan.analysis.support.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import io.atworks.specscan.analysis.domain.recipe.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class SemanticRecipeCompiler {
    private final RecipeSchemaValidator validator;
    public SemanticRecipeCompiler() { this(new RecipeSchemaValidator()); }
    public SemanticRecipeCompiler(RecipeSchemaValidator validator) { this.validator = validator; }

    public RecipeCompilationResult compile(YamlRecipeSourceLoader.LoadedRecipeSources loaded,
                                           SemanticPrimitiveRegistry registry) {
        List<RecipeDiagnostic> diagnostics = new ArrayList<>(validator.validate(loaded));
        if (!diagnostics.isEmpty()) return new RecipeCompilationResult(null, diagnostics);
        JsonNode engine = loaded.engine(), semantic = loaded.semantic(), framework = loaded.framework();
        List<SemanticRecipe> recipes = parseRecipes(semantic.get("recipes"), diagnostics, registry);
        List<FrameworkCatalogEntry> catalog = parseCatalog(framework.get("catalog").get("guards"));
        if (!diagnostics.isEmpty()) return new RecipeCompilationResult(null, diagnostics);
        EngineRecipeConfiguration config = parseConfig(engine);
        RecipePackVersion version = new RecipePackVersion(1, semantic.get("pack_version").asText(), framework.get("pack_version").asText());
        String digest = digest(engine, semantic, framework);
        return new RecipeCompilationResult(new CompiledRecipePlan(version, config, recipes, catalog, digest), List.of());
    }

    private List<SemanticRecipe> parseRecipes(JsonNode nodes, List<RecipeDiagnostic> diagnostics, SemanticPrimitiveRegistry registry) {
        List<SemanticRecipe> result = new ArrayList<>();
        for (JsonNode recipe : nodes) {
            List<RecipeStep> steps = new ArrayList<>(); Map<String, BindingType> bindings = new HashMap<>();
            bindings.put("predicate", BindingType.PREDICATE); bindings.put("candidate", BindingType.PREDICATE); bindings.put("graph", BindingType.UNKNOWN);
            for (JsonNode step : recipe.get("steps")) {
                String op = step.get("op").asText(); PrimitiveDescriptor descriptor = registry.descriptor(op).orElse(null); String path = "$.recipes[" + result.size() + "].steps";
                List<String> inputs = new ArrayList<>(); JsonNode input = step.get("in"); if (input != null) { if (input.isArray()) input.forEach(v -> inputs.add(v.asText())); else inputs.add(input.asText()); }
                if (descriptor == null) { diagnostics.add(new RecipeDiagnostic("semantic-recipes.yaml", path, "UNKNOWN_PRIMITIVE", op)); continue; }
                if (inputs.size() != descriptor.inputs().size()) diagnostics.add(new RecipeDiagnostic("semantic-recipes.yaml", path, "PRIMITIVE_INPUT_ARITY", op));
                for (String inputName : inputs) { BindingType actual = bindings.get(inputName); BindingType expected = descriptor.inputs().getOrDefault(inputName, BindingType.UNKNOWN); if (actual == null) diagnostics.add(new RecipeDiagnostic("semantic-recipes.yaml", path, "BINDING_NOT_DEFINED", inputName)); else if (expected != BindingType.UNKNOWN && actual != expected) diagnostics.add(new RecipeDiagnostic("semantic-recipes.yaml", path, "BINDING_TYPE_MISMATCH", inputName + " expected " + expected + " but was " + actual)); }
                BindingType output = descriptor.outputs().values().stream().findFirst().orElse(BindingType.UNKNOWN); bindings.put(step.get("out").asText(), output); steps.add(new RecipeStep(op, inputs, step.get("out").asText()));
            }
            if (diagnostics.isEmpty() || steps.size() == recipe.get("steps").size()) result.add(new SemanticRecipe(recipe.get("id").asText(), recipe.get("family").asText(), steps));
        }
        result.sort(Comparator.comparing(SemanticRecipe::id)); return List.copyOf(result);
    }
    private List<FrameworkCatalogEntry> parseCatalog(JsonNode nodes) { List<FrameworkCatalogEntry> result = new ArrayList<>(); for (JsonNode n : nodes) result.add(new FrameworkCatalogEntry(n.get("id").asText(), n.get("owner_type").asText(), n.get("method").asText(), n.get("argument_role").asText(), n.get("semantics").asText())); result.sort(Comparator.comparing(FrameworkCatalogEntry::id)); return List.copyOf(result); }
    private EngineRecipeConfiguration parseConfig(JsonNode root) { JsonNode e=root.get("engine"), t=e.get("traversal"), b=e.get("behavior"); return new EngineRecipeConfiguration(t.get("max_depth").asInt(), t.get("max_visited_methods_per_api").asInt(), t.get("max_edges_per_api").asInt(), b.get("deterministic_order").asBoolean(), b.get("report_unclassified").asBoolean(), b.get("report_truncation").asBoolean()); }
    private String digest(JsonNode... nodes) { String canonical = Arrays.stream(nodes).map(this::canonical).reduce("", (a,b)->a+"|"+b); try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(); for(byte value:hash) out.append(String.format("%02x", value)); return out.toString(); } catch(Exception e){throw new IllegalStateException(e);} }
    private String canonical(JsonNode node) { if(node==null||node.isNull())return"null"; if(node.isObject()){List<String> names=new ArrayList<>();node.fieldNames().forEachRemaining(names::add);names.sort(String::compareTo);StringBuilder out=new StringBuilder("{");for(String n:names)out.append('"').append(n).append('"').append(':').append(canonical(node.get(n))).append(',');return out.append('}').toString();}if(node.isArray()){StringBuilder out=new StringBuilder("[");node.forEach(v->out.append(canonical(v)).append(','));return out.append(']').toString();}return node.toString(); }
}
