package io.atworks.specscan.analysis.support.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import io.atworks.specscan.analysis.domain.recipe.*;
import java.util.*;

public final class RecipeSchemaValidator {
    private static final Set<String> ENGINE = Set.of("schema_version", "engine", "pack");
    private static final Set<String> ENGINE_BODY = Set.of("traversal", "behavior");
    private static final Set<String> TRAVERSAL = Set.of("max_depth", "max_visited_methods_per_api", "max_edges_per_api");
    private static final Set<String> BEHAVIOR = Set.of("deterministic_order", "report_unclassified", "report_truncation");
    private static final Set<String> SEMANTIC = Set.of("schema_version", "pack_version", "recipes");
    private static final Set<String> RECIPE = Set.of("id", "family", "steps");
    private static final Set<String> STEP = Set.of("op", "in", "out");
    private static final Set<String> FRAMEWORK = Set.of("schema_version", "pack_version", "catalog");
    private static final Set<String> CATALOG = Set.of("guards");
    private static final Set<String> GUARD = Set.of("id", "owner_type", "method", "argument_role", "semantics");
    private static final Set<String> FORBIDDEN = Set.of("expression", "custom_expression", "script", "callback", "reflection", "class_name", "java_code");

    public List<RecipeDiagnostic> validate(YamlRecipeSourceLoader.LoadedRecipeSources loaded) {
        List<RecipeDiagnostic> out = new ArrayList<>(loaded.diagnostics());
        validateEngine(loaded.engine(), out);
        validateSemantic(loaded.semantic(), out);
        validateFramework(loaded.framework(), out);
        return out.stream().sorted(Comparator.comparing(RecipeDiagnostic::file)
            .thenComparing(diagnostic -> Objects.toString(diagnostic.path(), ""))
            .thenComparing(RecipeDiagnostic::code)).toList();
    }

    private void validateEngine(JsonNode root, List<RecipeDiagnostic> out) {
        if (root == null) return;
        unknown(root, ENGINE, "engine-config.yaml", "$", out); version(root, "engine-config.yaml", out);
        JsonNode engine = requiredObject(root, "engine", "engine-config.yaml", "$", out);
        if (engine == null) return;
        unknown(engine, ENGINE_BODY, "engine-config.yaml", "$.engine", out);
        JsonNode traversal = requiredObject(engine, "traversal", "engine-config.yaml", "$.engine", out);
        if (traversal != null) {
            unknown(traversal, TRAVERSAL, "engine-config.yaml", "$.engine.traversal", out);
            for (String field : TRAVERSAL) positive(traversal, field, "engine-config.yaml", "$.engine.traversal", out);
        }
        JsonNode behavior = requiredObject(engine, "behavior", "engine-config.yaml", "$.engine", out);
        if (behavior != null) unknown(behavior, BEHAVIOR, "engine-config.yaml", "$.engine.behavior", out);
        forbidden(root, "engine-config.yaml", "$.", out);
    }

    private void validateSemantic(JsonNode root, List<RecipeDiagnostic> out) {
        if (root == null) return;
        unknown(root, SEMANTIC, "semantic-recipes.yaml", "$", out); version(root, "semantic-recipes.yaml", out);
        requiredText(root, "pack_version", "semantic-recipes.yaml", "$", out);
        JsonNode recipes = root.get("recipes");
        if (recipes == null || !recipes.isArray()) { out.add(diag("semantic-recipes.yaml", "$.recipes", "RECIPES_REQUIRED", "recipes must be an array")); return; }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < recipes.size(); i++) {
            String path = "$.recipes[" + i + "]"; JsonNode recipe = recipes.get(i);
            if (recipe == null || !recipe.isObject()) { out.add(diag("semantic-recipes.yaml", path, "INVALID_RECIPE", "recipe must be an object")); continue; }
            unknown(recipe, RECIPE, "semantic-recipes.yaml", path, out);
            String id = text(recipe, "id"); if (id == null) out.add(diag("semantic-recipes.yaml", path, "RECIPE_ID_REQUIRED", "id is required")); else if (!ids.add(id)) out.add(diag("semantic-recipes.yaml", path, "DUPLICATE_RECIPE_ID", id));
            requiredText(recipe, "family", "semantic-recipes.yaml", path, out);
            JsonNode steps = recipe.get("steps");
            if (steps == null || !steps.isArray() || steps.isEmpty()) { out.add(diag("semantic-recipes.yaml", path, "STEPS_REQUIRED", "steps must be a non-empty array")); continue; }
            for (int s = 0; s < steps.size(); s++) {
                String sp = path + ".steps[" + s + "]"; JsonNode step = steps.get(s);
                if (step == null || !step.isObject()) { out.add(diag("semantic-recipes.yaml", sp, "INVALID_STEP", "step must be an object")); continue; }
                unknown(step, STEP, "semantic-recipes.yaml", sp, out); requiredText(step, "op", "semantic-recipes.yaml", sp, out); requiredText(step, "out", "semantic-recipes.yaml", sp, out);
                JsonNode input = step.get("in"); if (input != null && !input.isTextual() && !input.isArray()) out.add(diag("semantic-recipes.yaml", sp, "INVALID_INPUT_BINDING", "in must be a binding or array of bindings"));
            }
        }
        forbidden(root, "semantic-recipes.yaml", "$.", out);
    }

    private void validateFramework(JsonNode root, List<RecipeDiagnostic> out) {
        if (root == null) return;
        unknown(root, FRAMEWORK, "framework-catalog.yaml", "$", out); version(root, "framework-catalog.yaml", out); requiredText(root, "pack_version", "framework-catalog.yaml", "$", out);
        JsonNode catalog = requiredObject(root, "catalog", "framework-catalog.yaml", "$", out);
        if (catalog == null) return; unknown(catalog, CATALOG, "framework-catalog.yaml", "$.catalog", out);
        JsonNode guards = catalog.get("guards"); if (guards == null || !guards.isArray()) { out.add(diag("framework-catalog.yaml", "$.catalog.guards", "GUARDS_REQUIRED", "guards must be an array")); return; }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < guards.size(); i++) { String path = "$.catalog.guards[" + i + "]"; JsonNode guard = guards.get(i); if (guard == null || !guard.isObject()) { out.add(diag("framework-catalog.yaml", path, "INVALID_GUARD", "guard must be an object")); continue; } unknown(guard, GUARD, "framework-catalog.yaml", path, out); String id = text(guard, "id"); if (id == null) out.add(diag("framework-catalog.yaml", path, "GUARD_ID_REQUIRED", "id is required")); else if (!ids.add(id)) out.add(diag("framework-catalog.yaml", path, "DUPLICATE_GUARD_ID", id)); for (String field : GUARD) requiredText(guard, field, "framework-catalog.yaml", path, out); }
        forbidden(root, "framework-catalog.yaml", "$.", out);
    }

    private void unknown(JsonNode node, Set<String> allowed, String file, String path, List<RecipeDiagnostic> out) { node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) out.add(diag(file, path + "." + field, "UNKNOWN_FIELD", "unknown field: " + field)); }); }
    private void forbidden(JsonNode node, String file, String path, List<RecipeDiagnostic> out) { node.fieldNames().forEachRemaining(field -> { if (FORBIDDEN.contains(field)) out.add(diag(file, path + field, "FORBIDDEN_FIELD", "forbidden field: " + field)); JsonNode child=node.get(field); if (child.isObject()) forbidden(child,file,path+field+".",out); else if(child.isArray()) child.forEach(item->{if(item.isObject()) forbidden(item,file,path+field+"[].",out);}); }); }
    private JsonNode requiredObject(JsonNode node, String field, String file, String path, List<RecipeDiagnostic> out) { JsonNode value=node.get(field); if(value==null||!value.isObject()){out.add(diag(file,path+"."+field,"OBJECT_REQUIRED",field+" must be an object"));return null;} return value; }
    private void version(JsonNode node, String file, List<RecipeDiagnostic> out) { if(node.get("schema_version")==null||!node.get("schema_version").canConvertToInt()||node.get("schema_version").asInt()!=1) out.add(diag(file,"$.schema_version","SCHEMA_VERSION_UNSUPPORTED","schema_version must be 1")); }
    private void positive(JsonNode node,String field,String file,String path,List<RecipeDiagnostic> out){JsonNode v=node.get(field);if(v==null||!v.canConvertToInt()||v.asInt()<=0)out.add(diag(file,path+"."+field,"POSITIVE_INTEGER_REQUIRED",field+" must be positive"));}
    private void requiredText(JsonNode node,String field,String file,String path,List<RecipeDiagnostic> out){if(text(node,field)==null)out.add(diag(file,path+"."+field,"FIELD_REQUIRED",field+" is required"));}
    private static String text(JsonNode node,String field){JsonNode v=node.get(field);return v!=null&&v.isTextual()&&!v.textValue().isBlank()?v.textValue():null;}
    private static RecipeDiagnostic diag(String file,String path,String code,String message){return new RecipeDiagnostic(file,path,code,message);}
}
