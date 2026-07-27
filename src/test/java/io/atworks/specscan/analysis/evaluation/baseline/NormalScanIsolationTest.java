package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import io.atworks.specscan.analysis.support.recipe.RecipePlanBootstrapService;
import io.atworks.specscan.analysis.domain.recipe.RecipeSourceSet;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class NormalScanIsolationTest {
    @TempDir Path temporary;

    @Test void invalidU01InputDoesNotChangeNormalSpringScan() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        String invalid = Files.readString(workspace.manifest()).replaceFirst("b1f5f036", "f1f5f036");
        Files.writeString(workspace.manifest(), invalid);
        BaselineRunResult failed = new SingleRunCoordinator().run(BaselineTestWorkspace.proposal(workspace));
        assertThat(failed.status()).isEqualTo(BaselineRunResult.RunStatus.FAILED_PREFLIGHT);

        Path invalidRecipeRoot = temporary.resolve("invalid-recipes");
        Files.createDirectories(invalidRecipeRoot);
        RecipeSourceSet invalidRecipes = new RecipeSourceSet(invalidRecipeRoot.resolve("engine.yaml"),
            invalidRecipeRoot.resolve("semantic.yaml"), invalidRecipeRoot.resolve("framework.yaml"));
        assertThat(new RecipePlanBootstrapService().bootstrap(invalidRecipes).plan()).isNull();

        Path catalog = workspace.root().resolve("corpora/catalog");
        Instant now = Instant.now();
        RepositorySource source = new RepositorySource(new RepositoryIdentity("test", "fixture", "catalog",
            "catalog", "pinned"), new WorkspaceContext("normal", catalog.toString(), now, "local", false),
            List.of(new SourceRootCandidate("main", "src/main/java", "Gradle", true, 1, 1, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(3, 3, 3, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 0, "LOCAL", "pinned", 0));
        assertThat(new SpringStaticScanService().scan(source).endpoints()).hasSize(6);
    }
}
