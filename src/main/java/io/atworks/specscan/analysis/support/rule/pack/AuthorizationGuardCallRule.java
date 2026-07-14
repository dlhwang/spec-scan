package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class AuthorizationGuardCallRule implements GraphRule {
    public static final String ID = "JAVA_AUTHORIZATION_GUARD_CALL";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }

    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null || !support.failureOnThen(condition.id())) return List.of();
        FactNode call = support.callOperand(condition.id()).orElse(null);
        if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                || payload.argumentCount() < 1) return List.of();
        String signature = call.typeResolution().resolvedSignature();
        String method = payload.methodName().toLowerCase(Locale.ROOT);
        boolean authorizationCall = method.contains("permission") || method.contains("authorized")
            || method.startsWith("can") || (signature != null && signature.toLowerCase(Locale.ROOT).contains("permission"));
        if (!authorizationCall) return List.of();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            "currentUser", "HAS_CANCELLATION_PERMISSION", List.of(), call.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.AUTHORIZATION,
            TargetResolutionStatus.RESOLVED, constraint, 1.0,
            support.evidence(predicate, call, EvidenceRole.CALL)));
    }
}
