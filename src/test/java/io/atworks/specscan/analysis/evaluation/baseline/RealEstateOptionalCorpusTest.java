package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class RealEstateOptionalCorpusTest {
    @TempDir Path temporary;

    @Test void missingLocalBindingIsAnExplicitOptionalExclusion() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        SingleRunCoordinator coordinator = new SingleRunCoordinator(new BaselineJsonCodec(),
            new BaselineManifestValidator(), new CorpusAccessGuard(), new CorpusIntegrityVerifier(),
            new EvaluationWorkspaceMaterializer(), (id, prepared, deadline, metrics) ->
                new JavaBaselineAnalysisAdapter.RawCorpusObservation(id, prepared.sourceDigest(),
                    java.util.List.of(), java.util.List.of()),
            new CanonicalSnapshotBuilder(), new BaselineDiffer(), new CoverageCalculator());
        BaselineRunResult result = coordinator.run(BaselineTestWorkspace.proposal(workspace));
        assertThat(result.status()).isEqualTo(BaselineRunResult.RunStatus.REVIEW_REQUIRED);
        assertThat(result.snapshot().corpusRuns()).filteredOn(corpus -> corpus.corpusId()
            .equals("realestate-optional")).singleElement().satisfies(corpus ->
                assertThat(corpus.availabilityStatus()).isEqualTo(BaselineObservation.AvailabilityStatus.EXCLUDED_OPTIONAL));
        assertThat(result.diagnostics()).extracting(BaselineDiagnostic::code).contains("EXCLUDED_OPTIONAL");
    }

    @Test void explicitPathAndDigestBindingAddsOptionalObservation() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary.resolve("fixture"));
        Path localRoot = temporary.resolve("local-root");
        Path repository = localRoot.resolve("realestate");
        Files.createDirectories(repository.resolve("src/main/java/local"));
        Files.writeString(repository.resolve("src/main/java/local/LocalController.java"),
            "package local; class LocalController {}");
        String digest = new CorpusIntegrityVerifier().digest(new CorpusAccessGuard().confine(localRoot, "realestate"));
        BaselineRunRequest base = BaselineTestWorkspace.proposal(workspace);
        BaselineRunRequest request = new BaselineRunRequest(base.purpose(), base.corpusManifestPath(),
            base.baselineManifestPath(), base.profile(), base.engineRevision(), base.allowedCorpusRoot(),
            base.evaluationWorkspaceRoot(), base.outputRoot(), Map.of("realestate-optional",
                new BaselineRunRequest.LocalCorpusBinding(localRoot, "realestate", digest, "local-pinned")));
        BaselineRunResult result = coordinator().run(request);
        assertThat(result.snapshot().corpusRuns()).filteredOn(corpus -> corpus.corpusId()
            .equals("realestate-optional")).singleElement().satisfies(corpus ->
                assertThat(corpus.availabilityStatus()).isEqualTo(BaselineObservation.AvailabilityStatus.AVAILABLE));
        assertThat(result.diagnostics()).extracting(BaselineDiagnostic::code).doesNotContain("EXCLUDED_OPTIONAL");
    }

    @Test void localBindingCannotBeCreatedWithoutExpectedDigest() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            new BaselineRunRequest.LocalCorpusBinding(temporary, "realestate", null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private SingleRunCoordinator coordinator() {
        return new SingleRunCoordinator(new BaselineJsonCodec(), new BaselineManifestValidator(),
            new CorpusAccessGuard(), new CorpusIntegrityVerifier(), new EvaluationWorkspaceMaterializer(),
            (id, prepared, deadline, metrics) -> new JavaBaselineAnalysisAdapter.RawCorpusObservation(
                id, prepared.sourceDigest(), java.util.List.of(), java.util.List.of()),
            new CanonicalSnapshotBuilder(), new BaselineDiffer(), new CoverageCalculator());
    }
}
