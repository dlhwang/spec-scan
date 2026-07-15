package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class SpringDataFindByIdOrElseThrowRule implements GraphRule {
    public static final String ID = "SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.SPRING_DATA_JPA; }
    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode terminal = support.node(predicate.conditionNodeId());
        if (terminal == null || predicate.predicateType() != PredicateType.LOOKUP_CHAIN) return List.of();
        FactNode lookup = support.operands(terminal.id()).stream()
            .filter(node -> node.type() == FactNodeType.METHOD_CALL).findFirst().orElse(null);
        if (lookup == null || !(lookup.payload() instanceof FactNodePayload.MethodCallPayload payload)
                || !"findById".equals(payload.methodName()) || !isSpringDataLookup(lookup.typeResolution())) return List.of();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY,
            null, null, List.of(), lookup.id());
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.EXISTENCE,
            RuleEffect.BUSINESS_RESTRICTION, TargetResolutionStatus.NOT_APPLICABLE, constraint, 1.0,
            support.evidence(predicate, terminal, EvidenceRole.CALL, lookup, EvidenceRole.CALL)));
    }
    private boolean isSpringDataLookup(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.startsWith("org.springframework.data.repository.") && signature.contains(".findById(");
    }
}
