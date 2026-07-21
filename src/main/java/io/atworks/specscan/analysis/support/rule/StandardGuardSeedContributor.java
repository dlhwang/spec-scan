package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class StandardGuardSeedContributor implements ValidationSeedContributor {
    public String id() { return "standard-guard"; }

    public List<PredicateCandidate> contribute(FactCodeGraph graph, MethodScope scope,
                                               List<PredicateCandidate> existingCandidates) {
        SeedContributorSupport support = new SeedContributorSupport(graph);
        Set<String> existing = new HashSet<>();
        existingCandidates.forEach(candidate -> existing.add(candidate.conditionNodeId()));
        List<PredicateCandidate> result = new ArrayList<>();
        for (FactNode call : support.scopedCalls(scope)) {
            if (!existing.contains(call.id()) && isStandardGuard(call)) {
                result.add(support.candidate(call, PredicateType.COMPOSITE, call));
            }
        }
        return result;
    }

    private boolean isStandardGuard(FactNode call) {
        String signature = call.typeResolution().resolvedSignature();
        if (call.typeResolution().status() == TypeResolutionStatus.RESOLVED && signature != null) {
            return signature.startsWith("java.util.Objects.requireNonNull")
                || signature.startsWith("com.google.common.base.Preconditions.checkNotNull")
                || signature.startsWith("org.springframework.util.Assert.notNull");
        }
        String text = call.snippet();
        return text != null && (text.contains("requireNonNull") || text.contains("checkNotNull")
            || text.contains("notNull"));
    }
}
