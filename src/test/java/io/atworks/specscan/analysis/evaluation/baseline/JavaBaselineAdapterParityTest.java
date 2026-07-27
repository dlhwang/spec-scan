package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class JavaBaselineAdapterParityTest {
    @TempDir Path temporary;

    @Test void repeatedJavaAdapterObservationKeepsEndpointOutputsAndCandidateIds() throws Exception {
        var materialized = BaselineTestWorkspace.materialize(temporary);
        CorpusManifest manifest = new BaselineJsonCodec().readCorpusManifest(materialized.manifest());
        CorpusManifest.CorpusEntry catalog = manifest.corpora().stream()
            .filter(entry -> entry.corpusId().equals("catalog")).findFirst().orElseThrow();
        var view = new CorpusAccessGuard().confine(materialized.root(), catalog.locator());
        var prepared1 = new EvaluationWorkspaceMaterializer().materialize(view,
            temporary.resolve("adapter-one"), "catalog", catalog.sourceRoots(), catalog.contentDigest());
        var prepared2 = new EvaluationWorkspaceMaterializer().materialize(view,
            temporary.resolve("adapter-two"), "catalog", catalog.sourceRoots(), catalog.contentDigest());
        JavaBaselineAnalysisAdapter adapter = new JavaBaselineAnalysisAdapter();
        var first = adapter.analyze("catalog", prepared1, new RunDeadline(Duration.ofMinutes(2)),
            new RunMetricsCollector());
        var second = adapter.analyze("catalog", prepared2, new RunDeadline(Duration.ofMinutes(2)),
            new RunMetricsCollector());
        assertThat(first.endpoints()).hasSize(6);
        assertThat(first.endpoints()).extracting(endpoint -> endpoint.output())
            .containsExactlyElementsOf(second.endpoints().stream().map(endpoint -> endpoint.output()).toList());
        assertThat(first.endpoints().stream().flatMap(endpoint -> endpoint.candidates().stream())
            .map(candidate -> candidate.candidateId()).toList())
            .containsExactlyElementsOf(second.endpoints().stream().flatMap(endpoint -> endpoint.candidates().stream())
                .map(candidate -> candidate.candidateId()).toList());
    }
}
