package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactNode;

public interface FailureOutcomePolicy {
    String id();
    FailureOutcomeDecision evaluate(FactCodeGraph graph, FactNode condition, FactNode outcome);
}
