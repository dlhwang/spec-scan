package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class DeterminismVerifier {
    private final Function<BaselineRunRequest, BaselineRunResult> runner;
    private final LongSupplier nanoTime;
    public DeterminismVerifier(SingleRunCoordinator coordinator) { this(coordinator::run, System::nanoTime); }
    public DeterminismVerifier(Function<BaselineRunRequest, BaselineRunResult> runner) {
        this(runner, System::nanoTime);
    }
    public DeterminismVerifier(Function<BaselineRunRequest, BaselineRunResult> runner, LongSupplier nanoTime) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public Verification verify(BaselineRunRequest request) {
        RunDeadline suite = new RunDeadline(Duration.ofMillis(request.profile().suiteTimeoutMillis()), nanoTime);
        List<BaselineRunResult> runs = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            suite.checkpoint("determinism-run-" + index);
            runs.add(runner.apply(request));
        }
        List<String> differences = new ArrayList<>();
        compare(runs.get(0), runs.get(1), "run1-run2", differences);
        compare(runs.get(0), runs.get(2), "run1-run3", differences);
        return new Verification(runs.get(0), new EvaluationResultBundle.DeterminismResult(
            differences.isEmpty(), 3, differences), List.copyOf(runs));
    }

    private void compare(BaselineRunResult left, BaselineRunResult right, String pair, List<String> differences) {
        if (left.snapshot() == null || right.snapshot() == null) {
            if (left.status() != right.status()) differences.add(pair + ":status " + left.status() + " != " + right.status());
            if ((left.snapshot() == null) != (right.snapshot() == null)) differences.add(pair + ":snapshot availability");
            return;
        }
        if (!left.snapshot().snapshotId().equals(right.snapshot().snapshotId()))
            differences.add(pair + ":snapshotId " + left.snapshot().snapshotId() + " != " + right.snapshot().snapshotId());
    }

    public record Verification(BaselineRunResult referenceRun,
                               EvaluationResultBundle.DeterminismResult result,
                               List<BaselineRunResult> runs) {
        public Verification { runs = List.copyOf(runs); }
    }
}
