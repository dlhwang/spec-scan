package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.List;

public interface ValidationCandidateDetector {
    List<PredicateCandidate> detect(FactCodeGraph graph, MethodScope scope);
}
