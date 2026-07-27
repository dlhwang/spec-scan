package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.BaselineDiagnostic;
import io.atworks.specscan.analysis.domain.evaluation.BaselineObservation;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public final class RunMetricsCollector {
    private final LongSupplier nanoSource;
    private final LongSupplier heapUsedSource;
    private final long started;
    private long observedPeak;
    private boolean memoryAvailable = true;
    private int graphs;
    private long nodes;
    private long edges;
    private int candidates;
    private final List<BaselineDiagnostic> diagnostics = new ArrayList<>();

    public RunMetricsCollector() {
        this(System::nanoTime, () -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }
    public RunMetricsCollector(LongSupplier nanoSource, LongSupplier heapUsedSource) {
        this.nanoSource = nanoSource; this.heapUsedSource = heapUsedSource; this.started = nanoSource.getAsLong();
        checkpoint("run", null, null);
    }
    public void checkpoint(String stage, String corpusId, String operationKey) {
        try { observedPeak = Math.max(observedPeak, heapUsedSource.getAsLong()); }
        catch (RuntimeException exception) {
            memoryAvailable = false;
            diagnostics.add(new BaselineDiagnostic("METRICS_COLLECTION_FAILED",
                BaselineDiagnostic.Severity.WARNING, stage, corpusId, operationKey, null, exception.getMessage()));
        }
    }
    public void addGraph(long nodeCount, long edgeCount, int candidateCount) {
        graphs++; nodes += nodeCount; edges += edgeCount; candidates += candidateCount;
    }
    public BaselineObservation.MetricsSnapshot snapshot() {
        return new BaselineObservation.MetricsSnapshot(Math.max(0, nanoSource.getAsLong() - started), observedPeak,
            graphs, nodes, edges, candidates, memoryAvailable);
    }
    public List<BaselineDiagnostic> diagnostics() { return List.copyOf(diagnostics); }
}
