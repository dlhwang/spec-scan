package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import io.atworks.specscan.analysis.domain.candidate.PredicateType;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class ImplicitOptionalSeedContributor implements ValidationSeedContributor {
    public String id() { return "implicit-optional"; }

    public List<PredicateCandidate> contribute(FactCodeGraph graph, MethodScope scope,
                                               List<PredicateCandidate> existingCandidates) {
        SeedContributorSupport support = new SeedContributorSupport(graph);
        List<PredicateCandidate> result = new ArrayList<>();
        for (FactNode call : support.scopedCalls(scope)) {
            if (!(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                    || !"orElseThrow".equals(payload.methodName())
                    || (!isJdkOptional(call.typeResolution()) && !hasSpringDataReceiver(graph, support, call.id()))) {
                continue;
            }
            result.add(support.candidate(call, PredicateType.LOOKUP_CHAIN, call));
        }
        return result;
    }

    private boolean isJdkOptional(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.contains("java.util.Optional") && signature.contains(".orElseThrow(");
    }

    private boolean hasSpringDataReceiver(FactCodeGraph graph, SeedContributorSupport support, String callId) {
        return graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(callId)
                && edge.type() == FactEdgeType.OPERAND_OF && "RECEIVER".equals(edge.role()))
            .map(edge -> support.node(edge.targetNodeId())).filter(Objects::nonNull)
            .anyMatch(receiver -> receiver.payload() instanceof FactNodePayload.MethodCallPayload payload
                && "findById".equals(payload.methodName()) && isSpringData(receiver.typeResolution()));
    }

    private boolean isSpringData(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.contains("org.springframework.data.repository") && signature.contains(".findById(");
    }
}
