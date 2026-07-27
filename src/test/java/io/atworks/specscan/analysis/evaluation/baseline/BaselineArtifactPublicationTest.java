package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class BaselineArtifactPublicationTest {
    @TempDir Path temporary;

    @Test void publishesCompletionLastAndReaderRejectsCorruption() throws Exception {
        BaselineRunResult run = new BaselineRunResult(BaselineRunResult.RunStatus.REVIEW_REQUIRED,
            BaselineCanonicalDiffTest.snapshot(), new BaselineDiffer().proposal(BaselineCanonicalDiffTest.snapshot()),
            List.of(), BaselineObservation.MetricsSnapshot.empty(), 1);
        EvaluationResultBundle bundle = new EvaluationResultBundle(1, "bundle-one", "input-one",
            EvaluationResultBundle.OverallStatus.REVIEW_REQUIRED, run, null, List.of());
        var rendered = new BaselineArtifactRenderer().render(bundle);
        Path root = new ArtifactBundlePublisher().publish(temporary, bundle, rendered);
        assertThat(root.resolve("completion-manifest.json")).isRegularFile();
        assertThat(new CompletedBundleReader().read(root).bundleId()).isEqualTo("bundle-one");
        assertThatThrownBy(() -> new ArtifactBundlePublisher().publish(temporary, bundle, rendered))
            .hasMessageContaining("ARTIFACT_PUBLICATION_FAILED");
        Files.writeString(root.resolve("summary.md"), "corrupt");
        assertThatThrownBy(() -> new CompletedBundleReader().read(root)).hasMessageContaining("CORRUPT_BUNDLE");
    }

    @Test void incompleteDirectoryIsNeverAccepted() throws Exception {
        Path incomplete = Files.createDirectory(temporary.resolve("incomplete"));
        Files.writeString(incomplete.resolve("evaluation-result.json"), "{}");
        assertThatThrownBy(() -> new CompletedBundleReader().read(incomplete))
            .hasMessageContaining("INCOMPLETE_BUNDLE");
    }
}
