package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class OptionalLookupFailureRule implements GraphRule {
    public static final String ID = "JDK_OPTIONAL_LOOKUP_FAILURE";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JDK_IDIOM; }

    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode call = support.node(predicate.conditionNodeId());
        if (predicate.predicateType() != PredicateType.LOOKUP_CHAIN || call == null
                || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                || !"orElseThrow".equals(payload.methodName()) || !isOptional(call.typeResolution())) return List.of();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY,
            null, null, List.of(), call.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.EXISTENCE,
            TargetResolutionStatus.NOT_APPLICABLE, constraint, 1.0,
            support.evidence(predicate, call, EvidenceRole.CALL)));
    }

    private boolean isOptional(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.startsWith("java.util.Optional.") && signature.contains("orElseThrow(");
    }
}
