package io.atworks.specscan.analysis.support.rule.yaml;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.*;

public final class YamlGraphRule implements GraphRule {
    private final YamlRuleDefinition definition;
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();
    private final BusinessRuleCandidateFactory candidateFactory = new BusinessRuleCandidateFactory();

    public YamlGraphRule(YamlRuleDefinition definition) { this.definition = Objects.requireNonNull(definition); }
    public String id() { return definition.id(); }
    public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }

    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        if (predicate.predicateType() != definition.predicateType()) return List.of();
        Map<String, FactNode> nodes = new HashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        if (!matchesFailureOutcome(graph, predicate.conditionNodeId())) return List.of();
        FactNode call = descendants(graph, nodes, predicate.conditionNodeId()).stream()
            .filter(node -> node.type() == FactNodeType.METHOD_CALL)
            .filter(this::matchesCall).findFirst().orElse(null);
        if (call == null) return List.of();

        List<EvidenceRef> evidence = new ArrayList<>(predicate.evidence());
        EvidenceRef callEvidence = evidenceMapper.fromFact(call, EvidenceRole.CALL);
        if (!evidence.contains(callEvidence)) evidence.add(callEvidence);
        NormalizedConstraint constraint = new NormalizedConstraint(
            ConstraintKind.CONTROL_FLOW_ONLY, null, null, List.of(), call.id());
        String fingerprint = evidence.stream().map(ref -> ref.nodeId() + ":" + ref.role()).sorted()
            .reduce((left, right) -> left + "|" + right).orElse(call.id());
        return List.of(candidateFactory.create(predicate.candidateId(), id(), definition.category(),
            RuleEffect.BUSINESS_RESTRICTION,
            predicate.extractionStatus(), SemanticStatus.RESOLVED, TargetResolutionStatus.NOT_APPLICABLE,
            constraint, 1.0, evidence, predicate.diagnostics(), fingerprint));
    }

    private boolean matchesCall(FactNode node) {
        if (!(node.payload() instanceof FactNodePayload.MethodCallPayload call)) return false;
        if (definition.methodName() != null && !definition.methodName().equals(call.methodName())) return false;
        String signature = node.typeResolution().resolvedSignature();
        return definition.resolvedSignatureContains() == null
            || signature != null && signature.contains(definition.resolvedSignatureContains());
    }

    private boolean matchesFailureOutcome(FactCodeGraph graph, String conditionId) {
        boolean thenOutcome = graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(conditionId)
            && edge.type() == FactEdgeType.THEN_OUTCOME);
        boolean elseOutcome = graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(conditionId)
            && edge.type() == FactEdgeType.ELSE_OUTCOME);
        return switch (definition.failureOutcome()) {
            case THEN -> thenOutcome;
            case ELSE -> elseOutcome;
            case ANY -> thenOutcome || elseOutcome;
        };
    }

    private static List<FactNode> descendants(FactCodeGraph graph, Map<String, FactNode> nodes, String sourceId) {
        List<FactNode> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(sourceId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(current)
                && (edge.type() == FactEdgeType.OPERAND_OF || edge.type() == FactEdgeType.ASSIGNED_FROM))
                .sorted(Comparator.comparingInt(FactEdge::ordinal)).forEach(edge -> {
                    FactNode target = nodes.get(edge.targetNodeId());
                    if (target != null) { result.add(target); pending.addLast(target.id()); }
                });
        }
        return result;
    }
}
