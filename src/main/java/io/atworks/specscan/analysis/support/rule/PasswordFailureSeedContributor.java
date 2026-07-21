package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.semantic.SpringSecurityPasswordMatcher;
import java.util.*;

public final class PasswordFailureSeedContributor implements ValidationSeedContributor {
    public String id() { return "password-failure"; }

    public List<PredicateCandidate> contribute(FactCodeGraph graph, MethodScope scope,
                                               List<PredicateCandidate> existingCandidates) {
        SeedContributorSupport support = new SeedContributorSupport(graph);
        Set<String> existing = new HashSet<>();
        existingCandidates.forEach(candidate -> existing.add(candidate.conditionNodeId()));
        List<PredicateCandidate> result = new ArrayList<>();
        for (FactNode condition : support.scopedConditions(scope)) {
            if (existing.contains(condition.id())) continue;
            boolean passwordCall = support.descendants(condition.id()).stream().anyMatch(node ->
                node.payload() instanceof FactNodePayload.MethodCallPayload call
                    && "matches".equals(call.methodName())
                    && SpringSecurityPasswordMatcher.matches(node.typeResolution()));
            FactNode mismatch = support.outcome(condition.id(), FactEdgeType.ELSE_OUTCOME);
            if (passwordCall && mismatch != null) {
                result.add(support.candidate(condition, PredicateType.COMPOSITE, mismatch));
            }
        }
        return result;
    }
}
