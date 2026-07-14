package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCandidate;
import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.List;

public interface GraphRule {
    String id();
    RuleLayer layer();
    List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate candidate);
}
