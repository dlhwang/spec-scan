package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.intelligence.ApiIntelligence;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.List;

public interface FactIntelligenceModelPort {

    ApiIntelligence analyze(String apiId, FactCodeGraph graph, List<Evidence> evidence);
}
