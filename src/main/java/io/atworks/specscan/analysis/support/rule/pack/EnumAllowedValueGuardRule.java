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
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload)) return List.of();
        List<FactNode> comparisons = comparisons(condition, support);
        if (comparisons.isEmpty() || !isAllowedSet(condition, comparisons, support)) return List.of();

        List<FactNode> constants = new ArrayList<>();
        Set<String> declaringTypes = new HashSet<>();
        Set<String> origins = new HashSet<>();
        List<String> expected = new ArrayList<>();
        for (FactNode comparison : comparisons) {
            List<FactNode> operands = support.operands(comparison.id());
            FactNode constant = operands.stream().filter(node -> node.type() == FactNodeType.ENUM_CONSTANT)
                .findFirst().orElse(null);
            FactNode value = operands.stream().filter(node -> node.type() != FactNodeType.ENUM_CONSTANT)
                .findFirst().orElse(null);
            if (constant == null || value == null || operands.size() != 2) return List.of();
            Optional<String> origin = support.originKey(value);
            if (origin.isEmpty()) return List.of();
            origins.add(origin.get()); constants.add(constant);
            FactNodePayload.EnumConstantPayload enumValue = (FactNodePayload.EnumConstantPayload) constant.payload();
            declaringTypes.add(enumValue.declaringType()); expected.add(enumValue.constantName());
        }
        if (declaringTypes.size() != 1 || origins.size() != 1) return List.of();

        Collections.sort(expected);
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.INPUT_LITERAL,
            null, "IN", expected, constants.stream().map(FactNode::id).sorted()
                .reduce((left, right) -> left + "," + right).orElse(null));
        Object[] evidence = new Object[constants.size() * 2];
        for (int i = 0; i < constants.size(); i++) {
            evidence[i * 2] = constants.get(i); evidence[i * 2 + 1] = EvidenceRole.DOMAIN_ORIGIN;
        }
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.STATE_PRECONDITION,
            TargetResolutionStatus.NOT_APPLICABLE, constraint, 1.0, support.evidence(predicate, evidence)));
    }

    private List<FactNode> comparisons(FactNode condition, StructuralRuleSupport support) {
        FactNodePayload.ConditionPayload payload = (FactNodePayload.ConditionPayload) condition.payload();
        if ("==".equals(payload.rootOperator()) || "!=".equals(payload.rootOperator())) return List.of(condition);
        if (!"&&".equals(payload.rootOperator())) return List.of();
        List<FactNode> children = support.operands(condition.id()).stream()
            .filter(node -> node.payload() instanceof FactNodePayload.ConditionPayload).toList();
        return children.size() >= 2 ? children : List.of();
    }

    private boolean isAllowedSet(FactNode root, List<FactNode> comparisons, StructuralRuleSupport support) {
        FactNodePayload.ConditionPayload payload = (FactNodePayload.ConditionPayload) root.payload();
        if ("!=".equals(payload.rootOperator())) return support.failureOnThen(root.id());
        if ("==".equals(payload.rootOperator())) return support.failureOnElse(root.id());
        if (!"&&".equals(payload.rootOperator()) || !support.failureOnThen(root.id())) return false;
        return comparisons.stream().allMatch(node -> node.payload() instanceof FactNodePayload.ConditionPayload child
            && "!=".equals(child.rootOperator()));
    }
}
