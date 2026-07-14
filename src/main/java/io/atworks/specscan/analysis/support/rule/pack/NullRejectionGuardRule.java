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
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) return List.of();
        List<FactNode> operands = support.operands(condition.id());
        FactNode nullNode = operands.stream().filter(node -> node.type() == FactNodeType.NULL_LITERAL).findFirst().orElse(null);
        FactNode value = operands.stream().filter(node -> node.type() != FactNodeType.NULL_LITERAL).findFirst().orElse(null);
        boolean rejectsNull = ("==".equals(payload.rootOperator()) && support.failureOnThen(condition.id()))
            || ("!=".equals(payload.rootOperator()) && support.failureOnElse(condition.id()));
        if (!rejectsNull || nullNode == null || value == null) return List.of();
        boolean input = support.readsParameter(value);
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY,
            input ? value.snippet() : null, "NOT_NULL", List.of(), nullNode.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.INVARIANT,
            input ? TargetResolutionStatus.RESOLVED : TargetResolutionStatus.NOT_APPLICABLE,
            constraint, 1.0, support.evidence(predicate, value,
                input ? EvidenceRole.INPUT_ORIGIN : EvidenceRole.DOMAIN_ORIGIN)));
    }
}
