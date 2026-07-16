package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class DelegatedGuardRule implements GraphRule {
    public static final String ID = "JAVA_DELEGATED_GUARD";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }

    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null) return List.of();
        FactNode call = support.callOperand(condition.id()).orElse(null);
        if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload)) return List.of();
        FactNode method = support.calledMethod(call.id()).orElse(null);
        if (method == null) return List.of();
        List<FactNode> returns = support.methodReturns(method.id());
        if (returns.isEmpty()) return List.of();

        List<FactNode> returnFacts = returns.stream().flatMap(node -> support.descendants(node.id()).stream()).toList();
        List<FactNode> enumConstants = returnFacts.stream()
            .filter(node -> node.type() == FactNodeType.ENUM_CONSTANT).toList();
        if (enumConstants.isEmpty()) {
            enumConstants = support.descendants(condition.id()).stream()
                .filter(node -> node.type() == FactNodeType.ENUM_CONSTANT).toList();
        }
        if (!enumConstants.isEmpty()) return List.of(stateRule(predicate, support, call, method, returns, enumConstants));
        if (isFieldParameterEquality(returnFacts, returns)) return List.of(versionRule(predicate, support, call, method, returns));
        return List.of();
    }

    private BusinessRuleCandidate stateRule(PredicateCandidate predicate, StructuralRuleSupport support,
                                            FactNode call, FactNode method, List<FactNode> returns,
                                            List<FactNode> constants) {
        Set<String> values = new TreeSet<>();
        for (FactNode constant : constants) {
            FactNodePayload.EnumConstantPayload payload = (FactNodePayload.EnumConstantPayload) constant.payload();
            values.add(payload.constantName());
        }
        FactNode field = returns.stream().flatMap(node -> support.descendants(node.id()).stream())
            .filter(node -> node.payload() instanceof FactNodePayload.FieldAccessPayload).findFirst().orElse(null);
        String target = field == null ? null : "order." + ((FactNodePayload.FieldAccessPayload) field.payload()).fieldName();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            target, "STATE_IN", List.copyOf(values), method.id());
        List<EvidenceRef> evidence = support.evidence(predicate, call, EvidenceRole.CALL,
            method, EvidenceRole.DOMAIN_ORIGIN);
        for (FactNode constant : constants) evidence = merge(evidence,
            support.evidence(predicate, constant, EvidenceRole.DOMAIN_ORIGIN));
        return support.resolved(predicate, ID, BusinessRuleCategory.STATE_PRECONDITION,
            RuleEffect.REQUEST_REQUIREMENT,
            target == null ? TargetResolutionStatus.UNRESOLVED : TargetResolutionStatus.RESOLVED,
            constraint, 1.0, evidence);
    }

    private BusinessRuleCandidate versionRule(PredicateCandidate predicate, StructuralRuleSupport support,
                                              FactNode call, FactNode method, List<FactNode> returns) {
        List<FactNode> arguments = support.callArguments(call.id());
        FactNode input = arguments.isEmpty() ? null : arguments.get(0);
        String target = input == null ? null : propertyName(input.snippet());
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            target, "OPTIMISTIC_LOCK_MATCH", List.of(), method.id());
        List<EvidenceRef> evidence = support.evidence(predicate, call, EvidenceRole.CALL,
            method, EvidenceRole.DOMAIN_ORIGIN);
        if (input != null) evidence = merge(evidence, support.evidence(predicate, input, EvidenceRole.INPUT_ORIGIN));
        return support.resolved(predicate, ID, BusinessRuleCategory.VERSION_CONSISTENCY,
            RuleEffect.REQUEST_REQUIREMENT,
            target == null ? TargetResolutionStatus.UNRESOLVED : TargetResolutionStatus.RESOLVED,
            constraint, 1.0, evidence);
    }

    private boolean isFieldParameterEquality(List<FactNode> facts, List<FactNode> returns) {
        boolean field = facts.stream().anyMatch(node -> node.type() == FactNodeType.FIELD_ACCESS);
        boolean equality = returns.stream().anyMatch(node -> node.snippet().contains("==") || node.snippet().contains("!="));
        return field && equality;
    }

    private String propertyName(String snippet) {
        if (snippet == null) return null;
        java.util.regex.Matcher getter = java.util.regex.Pattern.compile("get([A-Z][A-Za-z0-9_]*)\\(").matcher(snippet);
        if (getter.find()) return Character.toLowerCase(getter.group(1).charAt(0)) + getter.group(1).substring(1);
        if (snippet.matches("[A-Za-z_$][A-Za-z0-9_$]*")) return snippet;
        return null;
    }

    private List<EvidenceRef> merge(List<EvidenceRef> left, List<EvidenceRef> right) {
        Map<String, EvidenceRef> result = new LinkedHashMap<>();
        for (EvidenceRef value : left) result.put(value.nodeId() + value.role(), value);
        for (EvidenceRef value : right) result.put(value.nodeId() + value.role(), value);
        return List.copyOf(result.values());
    }
}
