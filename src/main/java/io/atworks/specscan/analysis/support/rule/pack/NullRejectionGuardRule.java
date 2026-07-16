package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class NullRejectionGuardRule implements GraphRule {
    public static final String ID = "JAVA_NULL_REJECTION_GUARD";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }
    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null) return List.of();
        FactNode value = null;
        boolean rejectsNull = false;
        String triggerNodeId = condition.id();
        if (condition.payload() instanceof FactNodePayload.ConditionPayload payload) {
            List<FactNode> operands = support.operands(condition.id());
            FactNode nullNode = operands.stream().filter(node -> node.type() == FactNodeType.NULL_LITERAL).findFirst().orElse(null);
            value = operands.stream().filter(node -> node.type() != FactNodeType.NULL_LITERAL).findFirst().orElse(null);
            rejectsNull = ("==".equals(payload.rootOperator()) && support.failureOnThen(condition.id()))
                || ("!=".equals(payload.rootOperator()) && support.failureOnElse(condition.id()));
            if (!rejectsNull || nullNode == null || value == null) return List.of();
        } else if (condition.payload() instanceof FactNodePayload.MethodCallPayload payload) {
            String name = payload.methodName();
            String signature = condition.typeResolution().resolvedSignature();
            boolean isNullCheck = false;
            if (condition.typeResolution().status() == TypeResolutionStatus.RESOLVED && signature != null) {
                if (signature.startsWith("java.util.Objects.requireNonNull")
                        || signature.startsWith("com.google.common.base.Preconditions.checkNotNull")
                        || signature.startsWith("org.springframework.util.Assert.notNull")) {
                    isNullCheck = true;
                }
            } else {
                String text = condition.snippet();
                if (text != null && (text.contains("requireNonNull") || text.contains("checkNotNull") || text.contains("notNull"))) {
                    isNullCheck = true;
                }
            }
            if (isNullCheck) {
                List<FactNode> args = support.callArguments(condition.id());
                if (!args.isEmpty()) {
                    value = args.get(0);
                    rejectsNull = true;
                }
            }
        }
        if (!rejectsNull || value == null) return List.of();
        var originPath = support.originPath(value);
        boolean input = originPath.isPresent();
        NormalizedConstraint constraint = new NormalizedConstraint(input
            ? ConstraintKind.INPUT_LITERAL : ConstraintKind.CONTROL_FLOW_ONLY,
            originPath.orElse(null), "NOT_NULL", List.of(), triggerNodeId);
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.INVARIANT,
            input ? RuleEffect.REQUEST_REQUIREMENT : RuleEffect.BUSINESS_RESTRICTION,
            input ? TargetResolutionStatus.RESOLVED : TargetResolutionStatus.NOT_APPLICABLE,
            constraint, 1.0, support.evidence(predicate, value,
                input ? EvidenceRole.INPUT_ORIGIN : EvidenceRole.DOMAIN_ORIGIN)));
    }
}
