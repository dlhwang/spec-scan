package io.atworks.specscan.analysis.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import io.atworks.specscan.analysis.support.recipe.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RecipePlanBootstrapTest {
    @TempDir Path temporary;

    @Test void compilesThreeStrictDocumentsIntoImmutableDeterministicPlan() throws Exception {
        RecipeSourceSet sources = copyFixture(temporary);
        RecipeCompilationResult first = new RecipePlanBootstrapService().bootstrap(sources);
        RecipeCompilationResult second = new RecipePlanBootstrapService().bootstrap(sources);
        assertThat(first.diagnostics()).as("diagnostics").isEmpty();
        assertThat(first.successful()).isTrue();
        assertThat(first.plan().contentDigest()).isEqualTo(second.plan().contentDigest());
        assertThat(first.plan().engine().maxDepth()).isEqualTo(15);
        assertThat(first.plan().engine().maxVisitedMethodsPerApi()).isEqualTo(500);
        assertThat(first.plan().engine().maxEdgesPerApi()).isEqualTo(10000);
        assertThatThrownBy(() -> first.plan().recipes().add(first.plan().recipes().get(0)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsUnknownForbiddenDuplicateAndInvalidBudgetBeforePlan() throws Exception {
        RecipeSourceSet sources = copyFixture(temporary);
        Files.writeString(sources.engineConfig(), Files.readString(sources.engineConfig()).replace("max_depth: 15", "max_depth: 0\n    custom_expression: nope"));
        Files.writeString(sources.semanticRecipes(), Files.readString(sources.semanticRecipes()).replace("- id: JAVA_BINARY_FAILURE_GUARD", "- id: JAVA_BINARY_FAILURE_GUARD\n    unknown_field: true"));
        RecipeCompilationResult result = new RecipePlanBootstrapService().bootstrap(sources);
        assertThat(result.plan()).isNull();
        assertThat(result.diagnostics()).extracting(RecipeDiagnostic::code)
            .contains("POSITIVE_INTEGER_REQUIRED", "UNKNOWN_FIELD", "FORBIDDEN_FIELD");
    }

    @Test void rejectsUnknownPrimitiveAndForwardBinding() throws Exception {
        RecipeSourceSet sources = copyFixture(temporary);
        String yaml = Files.readString(sources.semanticRecipes()).replace("match.failure_condition", "custom.unknown");
        Files.writeString(sources.semanticRecipes(), yaml);
        RecipeCompilationResult result = new RecipePlanBootstrapService().bootstrap(sources);
        assertThat(result.plan()).isNull();
        assertThat(result.diagnostics()).extracting(RecipeDiagnostic::code).contains("UNKNOWN_PRIMITIVE");
    }

    @Test void missingYamlIsEvaluationDiagnosticOnly() throws Exception {
        RecipeSourceSet missing = new RecipeSourceSet(temporary.resolve("missing-engine.yaml"),
            temporary.resolve("missing-semantic.yaml"), temporary.resolve("missing-framework.yaml"));
        RecipeCompilationResult result = new RecipePlanBootstrapService().bootstrap(missing);
        assertThat(result.plan()).isNull();
        assertThat(result.diagnostics()).extracting(RecipeDiagnostic::code)
            .containsOnly("YAML_SYNTAX_OR_READ_ERROR");
    }

    private RecipeSourceSet copyFixture(Path root) throws Exception {
        Path source = Path.of(getClass().getClassLoader().getResource("recipe-u02").toURI());
        Path target = root.resolve("recipe-u02"); Files.createDirectories(target);
        try (var paths = Files.walk(source)) { for (Path path : paths.filter(Files::isRegularFile).toList()) Files.copy(path, target.resolve(source.relativize(path))); }
        return new RecipeSourceSet(target.resolve("engine-config.yaml"), target.resolve("semantic-recipes.yaml"), target.resolve("framework-catalog.yaml"));
    }
}
