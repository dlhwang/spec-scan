package io.atworks.specscan.analysis.support.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.atworks.specscan.analysis.domain.recipe.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class YamlRecipeSourceLoader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public LoadedRecipeSources load(RecipeSourceSet sources) {
        List<RecipeDiagnostic> diagnostics = new ArrayList<>();
        JsonNode engine = read(sources.engineConfig(), "engine-config.yaml", diagnostics);
        JsonNode semantic = read(sources.semanticRecipes(), "semantic-recipes.yaml", diagnostics);
        JsonNode framework = read(sources.frameworkCatalog(), "framework-catalog.yaml", diagnostics);
        return new LoadedRecipeSources(sources, engine, semantic, framework,
            diagnostics.stream().sorted(Comparator.comparing(RecipeDiagnostic::file)
                .thenComparing(diagnostic -> Objects.toString(diagnostic.path(), ""))
                .thenComparing(RecipeDiagnostic::code)).toList());
    }

    private JsonNode read(Path path, String logicalName, List<RecipeDiagnostic> diagnostics) {
        try {
            String yaml = Files.readString(path, StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(yaml);
            if (root == null || !root.isObject()) {
                diagnostics.add(new RecipeDiagnostic(logicalName, null, "INVALID_ROOT", "YAML root must be an object"));
                return null;
            }
            return root;
        } catch (IOException | RuntimeException exception) {
            diagnostics.add(new RecipeDiagnostic(logicalName, null, "YAML_SYNTAX_OR_READ_ERROR",
                Objects.toString(exception.getMessage(), exception.getClass().getSimpleName())));
            return null;
        }
    }

    public record LoadedRecipeSources(RecipeSourceSet sourceSet, JsonNode engine, JsonNode semantic,
                                      JsonNode framework, List<RecipeDiagnostic> diagnostics) {
        public LoadedRecipeSources {
            diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        }
        public boolean readable() { return diagnostics.isEmpty() && engine != null && semantic != null && framework != null; }
    }
}
