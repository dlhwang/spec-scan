package io.atworks.apiintelligence.application;

import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.intelligence.ApiIntelligence;
import io.atworks.apiintelligence.port.out.IntelligenceModelPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiAnalysisOrchestrator {

    private final IntelligenceModelPort model;

    public ApiAnalysisOrchestrator(IntelligenceModelPort m) {
        model = m;
    }

    public Map<String, ApiIntelligence> analyze(Map<String, CodeGraph> graphs,
        Map<String, List<Evidence>> evidence) {
        Map<String, ApiIntelligence> out = new LinkedHashMap<>();
        for (var e : graphs.entrySet()) {
            out.put(e.getKey(), model.analyze(e.getKey(), e.getValue(),
                evidence.getOrDefault(e.getKey(), List.of())));
        }
        return out;
    }
}
