package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class U01CorpusEvaluationTest {
    @TempDir Path temporary;

    @Test void generatesReviewOnlyProposalForRequiredCorpusAndSeparatedHoldout() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        EvaluationResultBundle bundle = new BaselineEvaluationFacade().evaluate(BaselineTestWorkspace.proposal(workspace));
        assertThat(bundle.overallStatus()).isEqualTo(EvaluationResultBundle.OverallStatus.REVIEW_REQUIRED);
        assertThat(bundle.runResult().analyzerInvocations()).isEqualTo(6);
        assertThat(bundle.runResult().snapshot().corpusRuns()).hasSize(7);
        assertThat(bundle.runResult().snapshot().corpusRuns()).filteredOn(corpus ->
            corpus.availabilityStatus() == BaselineObservation.AvailabilityStatus.AVAILABLE)
            .allSatisfy(corpus -> assertThat(corpus.endpoints()).hasSize(6));
        assertThat(bundle.runResult().diff().verdict()).isEqualTo(BaselineDiff.Verdict.REVIEW_REQUIRED);
        assertThat(bundle.runResult().snapshot().coverage().graph().denominator()).isEqualTo(36);

        Path configured = configuredOutput();
        var rendered = new BaselineArtifactRenderer().render(bundle);
        Path existing = configured.resolve(bundle.bundleId());
        Path published = Files.exists(existing)
            ? existing : new ArtifactBundlePublisher().publish(configured, bundle, rendered);
        CompletionManifest completed = new CompletedBundleReader().read(published);
        assertThat(completed.overallStatus()).isEqualTo(EvaluationResultBundle.OverallStatus.REVIEW_REQUIRED);
        assertThat(completed.inputIdentity()).isEqualTo(bundle.inputIdentity());
    }

    private Path configuredOutput() {
        String configured = System.getProperty("specscan.u01.report.root");
        return configured == null || configured.isBlank() ? temporary.resolve("published") : Path.of(configured);
    }
}
