package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class InputDomainMismatchGuardRule implements GraphRule {
    public static final String ID = "INPUT_DOMAIN_VALUE_MISMATCH_GUARD";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }
    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) return List.of();
        boolean mismatchFails = ("!=".equals(payload.rootOperator()) && support.failureOnThen(condition.id()))
            || ("==".equals(payload.rootOperator()) && support.failureOnElse(condition.id()));
        List<FactNode> operands = support.operands(condition.id());
        if (!mismatchFails || operands.size() != 2) return List.of();
        FactNode left = operands.get(0), right = operands.get(1);
        boolean leftInput = support.readsParameter(left), rightInput = support.readsParameter(right);
        if (leftInput == rightInput || left.type() == FactNodeType.NULL_LITERAL || right.type() == FactNodeType.NULL_LITERAL
                || left.type() == FactNodeType.LITERAL || right.type() == FactNodeType.LITERAL) return List.of();
        FactNode input = leftInput ? left : right; FactNode domain = leftInput ? right : left;
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.INPUT_TO_DOMAIN,
            input.snippet(), "EQUALS", List.of(), domain.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.INVARIANT,
            RuleEffect.BUSINESS_RESTRICTION, TargetResolutionStatus.RESOLVED, constraint, 1.0,
            support.evidence(predicate, input, EvidenceRole.INPUT_ORIGIN, domain, EvidenceRole.DOMAIN_ORIGIN)));
    }
}
