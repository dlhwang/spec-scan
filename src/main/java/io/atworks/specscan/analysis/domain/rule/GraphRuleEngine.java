package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;

public interface GraphRuleEngine {
    GraphRuleEngineResult evaluate(FactCodeGraph graph, MethodScope scope);
}
