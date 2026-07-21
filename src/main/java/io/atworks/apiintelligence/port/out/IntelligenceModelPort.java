package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.intelligence.ApiIntelligence;
import java.util.List;

public interface IntelligenceModelPort {

    ApiIntelligence analyze(String apiId, CodeGraph graph, List<Evidence> evidence);
}
