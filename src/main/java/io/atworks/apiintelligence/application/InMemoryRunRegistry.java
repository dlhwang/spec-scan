package io.atworks.apiintelligence.application;

import io.atworks.apiintelligence.domain.run.AnalysisRunStatus;
import io.atworks.apiintelligence.port.out.RunProgressPort;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InMemoryRunRegistry implements RunProgressPort {

    private final ConcurrentMap<String, State> runs = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public AnalysisRunSnapshot create(String id) {
        if (shutdown.get()) {
            throw new IllegalStateException("shutdown");
        }
        if (runs.putIfAbsent(id, new State(id)) != null) {
            throw new IllegalArgumentException("duplicate run");
        }
        return runs.get(id).snapshot();
    }

    public AnalysisRunSnapshot transition(String id, AnalysisRunStatus s) {
        State r = req(id);
        synchronized (r) {
            if (r.status == AnalysisRunStatus.COMPLETED || r.status == AnalysisRunStatus.PARTIAL
                || r.status == AnalysisRunStatus.FAILED) {
                throw new IllegalStateException("terminal run");
            }
            r.status = s;
            return r.snapshot();
        }
    }

    public AnalysisRunSnapshot get(String id) {
        return req(id).snapshot();
    }

    public void shutdown() {
        shutdown.set(true);
    }

    private State req(String id) {
        State s = runs.get(id);
        if (s == null) {
            throw new IllegalArgumentException("unknown run");
        }
        return s;
    }

    private static final class State {

        final String id;
        volatile AnalysisRunStatus status = AnalysisRunStatus.QUEUED;

        State(String i) {
            id = i;
        }

        AnalysisRunSnapshot snapshot() {
            return new AnalysisRunSnapshot(id, status, 0, 0, null);
        }
    }
}
