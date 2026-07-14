package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class EnumAllowedValueGuardRule implements GraphRule {
    public static final String ID = "JAVA_ENUM_ALLOWED_VALUE_GUARD";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }
    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) return List.of();
        List<FactNode> descendants = support.descendants(condition.id());
        List<FactNode> constants = descendants.stream().filter(node -> node.type() == FactNodeType.ENUM_CONSTANT).toList();
        if (constants.isEmpty()) return List.of();
        Set<String> declaringTypes = new HashSet<>();
        List<String> expected = new ArrayList<>();
        for (FactNode constant : constants) {
            FactNodePayload.EnumConstantPayload enumValue = (FactNodePayload.EnumConstantPayload) constant.payload();
            declaringTypes.add(enumValue.declaringType()); expected.add(enumValue.constantName());
        }
        if (declaringTypes.size() != 1 || !isAllowedSet(payload, condition.snippet(), support, condition.id())) return List.of();
        Collections.sort(expected);
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.INPUT_LITERAL,
            null, "IN", expected, constants.stream().map(FactNode::id).sorted().reduce((a, b) -> a + "," + b).orElse(null));
        Object[] evidence = new Object[constants.size() * 2];
        for (int i = 0; i < constants.size(); i++) { evidence[i * 2] = constants.get(i); evidence[i * 2 + 1] = EvidenceRole.DOMAIN_ORIGIN; }
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.STATE_PRECONDITION,
            TargetResolutionStatus.NOT_APPLICABLE, constraint, 0.95, support.evidence(predicate, evidence)));
    }
    private boolean isAllowedSet(FactNodePayload.ConditionPayload payload, String snippet,
                                 StructuralRuleSupport support, String conditionId) {
        if ("!=".equals(payload.rootOperator())) return support.failureOnThen(conditionId);
        if ("==".equals(payload.rootOperator())) return support.failureOnElse(conditionId);
        if ("&&".equals(payload.rootOperator()) && support.failureOnThen(conditionId))
            return snippet.contains(" != ") && !snippet.contains(" == ");
        return false;
    }
}
