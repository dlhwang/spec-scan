package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.*;

final class StructuralRuleSupport {
    private final FactCodeGraph graph;
    private final Map<String, FactNode> nodes = new HashMap<>();
    private final EvidenceMapper evidence = new EvidenceMapper();
    private final BusinessRuleCandidateFactory candidates = new BusinessRuleCandidateFactory();

    StructuralRuleSupport(FactCodeGraph graph) {
        this.graph = graph;
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
    }

    FactNode node(String id) { return nodes.get(id); }

    List<FactNode> operands(String sourceId) {
        return graph.edges().stream()
            .filter(edge -> edge.type() == FactEdgeType.OPERAND_OF && edge.sourceNodeId().equals(sourceId))
            .sorted(Comparator.comparingInt(FactEdge::ordinal))
            .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull).toList();
    }

    List<FactNode> callArguments(String callId) {
        return graph.edges().stream()
            .filter(edge -> edge.type() == FactEdgeType.OPERAND_OF && edge.sourceNodeId().equals(callId)
                && "ARGUMENT".equals(edge.role()))
            .sorted(Comparator.comparingInt(FactEdge::ordinal))
            .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull).toList();
    }

    Optional<FactNode> callOperand(String conditionId) {
        return descendants(conditionId).stream().filter(node -> node.type() == FactNodeType.METHOD_CALL).findFirst();
    }

    List<FactNode> descendants(String sourceId) {
        List<FactNode> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(sourceId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (FactEdge edge : graph.edges()) if (edge.sourceNodeId().equals(current)
                    && (edge.type() == FactEdgeType.OPERAND_OF || edge.type() == FactEdgeType.ASSIGNED_FROM)) {
                FactNode target = nodes.get(edge.targetNodeId());
                if (target != null) { result.add(target); pending.addLast(target.id()); }
            }
        }
        return result;
    }

    boolean readsParameter(FactNode node) {
        return graph.edges().stream().anyMatch(edge -> edge.type() == FactEdgeType.READS
            && edge.sourceNodeId().equals(node.id()) && nodes.get(edge.targetNodeId()) != null
            && nodes.get(edge.targetNodeId()).type() == FactNodeType.PARAMETER);
    }

    Optional<String> originKey(FactNode node) {
        Optional<String> declaration = graph.edges().stream()
            .filter(edge -> edge.type() == FactEdgeType.READS && edge.sourceNodeId().equals(node.id()))
            .map(FactEdge::targetNodeId).sorted().findFirst();
        if (declaration.isPresent()) return declaration;
        if (node.payload() instanceof FactNodePayload.FieldAccessPayload field)
            return Optional.of("FIELD:" + field.rootExpressionKind() + ":" + field.fieldName());
        return Optional.empty();
    }

    boolean failureOnThen(String conditionId) {
        return graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(conditionId)
            && edge.type() == FactEdgeType.THEN_OUTCOME);
    }

    boolean failureOnElse(String conditionId) {
        return graph.edges().stream().anyMatch(edge -> edge.sourceNodeId().equals(conditionId)
            && edge.type() == FactEdgeType.ELSE_OUTCOME);
    }

    List<EvidenceRef> evidence(PredicateCandidate predicate, Object... additions) {
        List<EvidenceRef> result = new ArrayList<>(predicate.evidence());
        for (int i = 0; i < additions.length; i += 2) {
            FactNode node = (FactNode) additions[i];
            EvidenceRole role = (EvidenceRole) additions[i + 1];
            if (result.stream().noneMatch(ref -> ref.nodeId().equals(node.id()) && ref.role() == role))
                result.add(evidence.fromFact(node, role));
        }
        return result;
    }

    BusinessRuleCandidate resolved(PredicateCandidate predicate, String ruleId, BusinessRuleCategory category,
                                   TargetResolutionStatus targetStatus, NormalizedConstraint constraint,
                                   double confidence, List<EvidenceRef> evidence) {
        return resolved(predicate, ruleId, category, targetStatus, constraint, confidence, evidence,
            predicate.diagnostics());
    }

    BusinessRuleCandidate resolved(PredicateCandidate predicate, String ruleId, BusinessRuleCategory category,
                                   TargetResolutionStatus targetStatus, NormalizedConstraint constraint,
                                   double confidence, List<EvidenceRef> evidence,
                                   List<CandidateDiagnostic> diagnostics) {
        String fingerprint = evidence.stream().map(ref -> ref.nodeId() + ":" + ref.role()).sorted()
            .reduce((left, right) -> left + "|" + right).orElse(predicate.conditionNodeId());
        return candidates.create(predicate.candidateId(), ruleId, category, predicate.extractionStatus(),
            SemanticStatus.RESOLVED, targetStatus, constraint, confidence, evidence,
            diagnostics, fingerprint);
    }
}
