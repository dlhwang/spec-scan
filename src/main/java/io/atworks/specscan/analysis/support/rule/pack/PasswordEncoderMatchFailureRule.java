package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class PasswordEncoderMatchFailureRule implements GraphRule {
    public static final String ID = "SPRING_SECURITY_PASSWORD_MATCH_FAILURE";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.SPRING; }
    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)
                || !support.failureOnThen(condition.id()) || !"!".equals(payload.rootOperator())) return List.of();
        FactNode call = support.callOperand(condition.id()).orElse(null);
        if (call == null || !isPasswordMatch(call.typeResolution())) return List.of();
        List<FactNode> arguments = support.callArguments(call.id());
        if (arguments.size() != 2 || !support.readsParameter(arguments.get(0))) return List.of();
        FactNode input = arguments.get(0); FactNode stored = arguments.get(1);
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            input.snippet(), null, List.of(), call.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.AUTHENTICATION,
            TargetResolutionStatus.RESOLVED, constraint, 1.0,
            support.evidence(predicate, call, EvidenceRole.CALL, input, EvidenceRole.INPUT_ORIGIN,
                stored, EvidenceRole.DOMAIN_ORIGIN)));
    }
    private boolean isPasswordMatch(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.contains("org.springframework.security.crypto.password")
            && signature.endsWith(".matches(java.lang.CharSequence, java.lang.String)");
    }
}
