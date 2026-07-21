package io.atworks.specscan.analysis.domain.rule;

import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.List;

public interface ValidationSeedContributor {
    String id();
    List<PredicateCandidate> contribute(FactCodeGraph graph, MethodScope scope,
                                        List<PredicateCandidate> existingCandidates);
}
