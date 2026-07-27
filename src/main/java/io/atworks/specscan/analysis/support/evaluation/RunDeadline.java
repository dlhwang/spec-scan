package io.atworks.specscan.analysis.support.evaluation;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class RunDeadline {
    private final LongSupplier nanoSource;
    private final long deadlineNanos;

    public RunDeadline(Duration timeout) { this(timeout, System::nanoTime); }
    public RunDeadline(Duration timeout, LongSupplier nanoSource) {
        Objects.requireNonNull(timeout, "timeout"); this.nanoSource = Objects.requireNonNull(nanoSource, "nanoSource");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        long start = nanoSource.getAsLong();
        this.deadlineNanos = Math.addExact(start, timeout.toNanos());
    }
    public long remainingNanos() { return Math.max(0, deadlineNanos - nanoSource.getAsLong()); }
    public void checkpoint(String scope) {
        if (remainingNanos() == 0) throw new EvaluationTimeoutException(scope);
    }
    public static final class EvaluationTimeoutException extends RuntimeException {
        private final String scope;
        public EvaluationTimeoutException(String scope) { super("EVALUATION_TIMEOUT: " + scope); this.scope = scope; }
        public String scope() { return scope; }
    }
}
