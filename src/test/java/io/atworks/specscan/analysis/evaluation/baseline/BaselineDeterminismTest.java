package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.DeterminismVerifier;
import io.atworks.specscan.analysis.support.evaluation.RunDeadline;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BaselineDeterminismTest {
    @Test void invokesExactlyThreeRunsAndComparesCanonicalSnapshotIds() {
        AtomicInteger calls = new AtomicInteger();
        BaselineRunResult run = new BaselineRunResult(BaselineRunResult.RunStatus.PASS,
            BaselineCanonicalDiffTest.snapshot(), null, List.of(), BaselineObservation.MetricsSnapshot.empty(), 1);
        DeterminismVerifier verifier = new DeterminismVerifier(request -> { calls.incrementAndGet(); return run; });
        BaselineRunRequest request = new BaselineRunRequest(BaselineRunRequest.RunPurpose.G01_DETERMINISM,
            Path.of("corpus"), Path.of("baseline"), EvaluationProfile.g01(), "engine",
            Path.of("allowed"), Path.of("eval"), Path.of("output"));
        var result = verifier.verify(request);
        assertThat(calls).hasValue(3); assertThat(result.result().invocationCount()).isEqualTo(3);
        assertThat(result.result().deterministic()).isTrue();
    }

    @Test void reportsInjectedNondeterminismAgainstRunOne() {
        AtomicInteger calls = new AtomicInteger();
        DeterminismVerifier verifier = new DeterminismVerifier(request -> run(calls.incrementAndGet() == 2 ? "other" : "s"));
        var result = verifier.verify(request(EvaluationProfile.g01()));
        assertThat(result.result().deterministic()).isFalse();
        assertThat(result.result().differences()).singleElement().asString().contains("run1-run2:snapshotId");
        assertThat(calls).hasValue(3);
    }

    @Test void suiteDeadlineStopsBeforeStartingAnotherRunAndDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong nanos = new AtomicLong();
        DeterminismVerifier verifier = new DeterminismVerifier(request -> {
            calls.incrementAndGet(); nanos.set(3_000_000); return run("s");
        }, nanos::get);
        assertThatThrownBy(() -> verifier.verify(request(new EvaluationProfile("short", 1, 2))))
            .isInstanceOf(RunDeadline.EvaluationTimeoutException.class);
        assertThat(calls).hasValue(1);
    }

    private static BaselineRunResult run(String snapshotId) {
        BaselineObservation.Snapshot source = BaselineCanonicalDiffTest.snapshot();
        BaselineObservation.Snapshot snapshot = new BaselineObservation.Snapshot(snapshotId,
            source.manifestVersion(), source.engineRevision(), source.corpusRuns(), source.diagnostics(),
            source.coverage(), source.metrics());
        return new BaselineRunResult(BaselineRunResult.RunStatus.PASS, snapshot, null, List.of(),
            BaselineObservation.MetricsSnapshot.empty(), 1);
    }

    private static BaselineRunRequest request(EvaluationProfile profile) {
        return new BaselineRunRequest(BaselineRunRequest.RunPurpose.G01_DETERMINISM,
            Path.of("corpus"), Path.of("baseline"), profile, "engine",
            Path.of("allowed"), Path.of("eval"), Path.of("output"));
    }
}
