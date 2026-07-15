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
                || !"orElseThrow".equals(payload.methodName())) return List.of();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY,
            null, null, List.of(), call.id());
        List<EvidenceRef> evidence = support.evidence(predicate, call, EvidenceRole.CALL);
        FactNode lookup = support.operands(call.id()).stream()
            .filter(node -> node.type() == FactNodeType.METHOD_CALL).findFirst().orElse(null);
        if (lookup != null) {
            List<FactNode> arguments = support.callArguments(lookup.id());
            if (!arguments.isEmpty() && support.readsParameter(arguments.get(0)))
                evidence = support.evidence(predicate, call, EvidenceRole.CALL,
                    arguments.get(0), EvidenceRole.INPUT_ORIGIN);
        }
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.EXISTENCE,
            RuleEffect.BUSINESS_RESTRICTION, TargetResolutionStatus.NOT_APPLICABLE, constraint, 1.0,
            evidence));
    }

}
