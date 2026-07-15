package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class EmptyRejectionGuardRule implements GraphRule {
    public static final String ID = "JAVA_EMPTY_REJECTION_GUARD";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }

    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null || !support.failureOnThen(condition.id())) return List.of();
        FactNode call = support.callOperand(condition.id()).orElse(null);
        if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                || !"isEmpty".equals(payload.methodName()) || !isSupportedEmptyCall(call)) return List.of();
        List<FactNode> arguments = support.callArguments(call.id());
        if (arguments.size() != 1) return List.of();
        FactNode value = arguments.get(0);
        var originPath = support.originPath(value);
        boolean input = originPath.isPresent();
        NormalizedConstraint constraint = new NormalizedConstraint(input
            ? ConstraintKind.INPUT_LITERAL : ConstraintKind.CONTROL_FLOW_ONLY,
            originPath.orElse(null), "NOT_EMPTY", List.of(), call.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.INVARIANT,
            input ? TargetResolutionStatus.RESOLVED : TargetResolutionStatus.NOT_APPLICABLE,
            constraint, 1.0, support.evidence(predicate, call, EvidenceRole.CALL, value,
                input ? EvidenceRole.INPUT_ORIGIN : EvidenceRole.DOMAIN_ORIGIN)));
    }

    private boolean isSupportedEmptyCall(FactNode call) {
        String signature = call.typeResolution().resolvedSignature();
        return signature != null && (signature.contains("org.springframework.util.ObjectUtils.isEmpty")
            || signature.contains("org.springframework.util.StringUtils.isEmpty"))
            || call.snippet().startsWith("ObjectUtils.isEmpty(")
            || call.snippet().startsWith("StringUtils.isEmpty(");
    }
}
