package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.support.evaluation.*;
import io.atworks.specscan.analysis.domain.evaluation.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class BaselineExecutionControlTest {
    @TempDir Path temporary;
    @Test void deadlineUsesMonotonicCooperativeCheckpoints() {
        AtomicLong nanos = new AtomicLong();
        RunDeadline deadline = new RunDeadline(Duration.ofNanos(10), nanos::get);
        nanos.set(9); assertThatCode(() -> deadline.checkpoint("before")).doesNotThrowAnyException();
        nanos.set(10); assertThatThrownBy(() -> deadline.checkpoint("after"))
            .isInstanceOf(RunDeadline.EvaluationTimeoutException.class)
            .hasMessageContaining("EVALUATION_TIMEOUT");
    }

    @Test void metricFailureIsDiagnosticOnly() {
        AtomicLong time = new AtomicLong();
        RunMetricsCollector metrics = new RunMetricsCollector(time::get, () -> { throw new IllegalStateException("mx"); });
        metrics.checkpoint("endpoint", "c", "GET /x");
        assertThat(metrics.snapshot().memoryAvailable()).isFalse();
        assertThat(metrics.diagnostics()).extracting(diagnostic -> diagnostic.code())
            .containsOnly("METRICS_COLLECTION_FAILED", "METRICS_COLLECTION_FAILED");
    }

    @Test void corporaRunSequentiallyInCanonicalOrderWithoutRetry() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        List<String> order = new ArrayList<>();
        SingleRunCoordinator coordinator = coordinator((id, prepared, deadline, metrics) -> {
            order.add(id);
            return new JavaBaselineAnalysisAdapter.RawCorpusObservation(id, prepared.sourceDigest(), List.of(), List.of());
        });
        BaselineRunResult result = coordinator.run(BaselineTestWorkspace.proposal(workspace));
        assertThat(result.analyzerInvocations()).isEqualTo(6);
        assertThat(order).containsExactly("billing", "catalog", "catalog-renamed-holdout",
            "membership", "registry", "shipping");

        AtomicInteger failedCalls = new AtomicInteger();
        BaselineRunResult failed = coordinator((id, prepared, deadline, metrics) -> {
            failedCalls.incrementAndGet();
            throw new IllegalStateException("injected failure");
        }).run(BaselineTestWorkspace.proposal(workspace));
        assertThat(failed.status()).isEqualTo(BaselineRunResult.RunStatus.FAILED_ANALYSIS);
        assertThat(failed.analyzerInvocations()).isOne();
        assertThat(failedCalls).hasValue(1);
    }

    private SingleRunCoordinator coordinator(SingleRunCoordinator.CorpusAnalyzer analyzer) {
        return new SingleRunCoordinator(new BaselineJsonCodec(), new BaselineManifestValidator(),
            new CorpusAccessGuard(), new CorpusIntegrityVerifier(), new EvaluationWorkspaceMaterializer(), analyzer,
            new CanonicalSnapshotBuilder(), new BaselineDiffer(), new CoverageCalculator());
    }
}
