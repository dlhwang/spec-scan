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

    Optional<FactNode> calledMethod(String callId) {
        return graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.CALLS
                && edge.sourceNodeId().equals(callId)).map(edge -> nodes.get(edge.targetNodeId()))
            .filter(Objects::nonNull).filter(node -> node.type() == FactNodeType.METHOD).findFirst();
    }

    List<FactNode> methodReturns(String methodId) {
        return graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.RETURNS
                && edge.sourceNodeId().equals(methodId)).map(edge -> nodes.get(edge.targetNodeId()))
            .filter(Objects::nonNull).toList();
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
        return originPath(node).isPresent();
    }

    Optional<String> originPath(FactNode node) { return originPath(node, new HashSet<>()); }

    private Optional<String> originPath(FactNode node, Set<String> visited) {
        if (node == null || !visited.add(node.id())) return Optional.empty();
        if (node.type() == FactNodeType.PARAMETER) {
            Optional<FactEdge> propagated = graph.edges().stream()
                .filter(edge -> edge.type() == FactEdgeType.ORIGINATES_FROM
                    && edge.sourceNodeId().equals(node.id())
                    && ("CALL_ARGUMENT".equals(edge.role()) || "COLLECTION_ELEMENT".equals(edge.role())))
                .sorted(Comparator.comparing(FactEdge::role).thenComparing(FactEdge::targetNodeId)).findFirst();
            if (propagated.isEmpty()) return Optional.of("$");
            Optional<String> path = originPath(nodes.get(propagated.get().targetNodeId()), visited);
            return "COLLECTION_ELEMENT".equals(propagated.get().role())
                ? path.map(value -> value + "[*]") : path;
        }
        if (node.payload() instanceof FactNodePayload.MethodCallPayload call) {
            Optional<FactNode> receiver = graph.edges().stream()
                .filter(edge -> edge.sourceNodeId().equals(node.id())
                    && edge.type() == FactEdgeType.OPERAND_OF && "RECEIVER".equals(edge.role()))
                .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull).findFirst();
            Optional<String> base = receiver.flatMap(value -> originPath(value, visited));
            if ("get".equals(call.methodName()) && call.argumentCount() == 1)
                return base.map(value -> value + "[*]");
            String property = getterProperty(call.methodName());
            return property == null ? base : base.map(value -> append(value, property));
        }
        Optional<FactNode> declaration = graph.edges().stream()
            .filter(edge -> edge.type() == FactEdgeType.READS && edge.sourceNodeId().equals(node.id()))
            .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull).findFirst();
        if (declaration.isPresent()) return originPath(declaration.get(), visited);
        if (node.type() == FactNodeType.VALUE_FIELD) {
            Optional<FactNode> value = graph.edges().stream()
                .filter(edge -> edge.type() == FactEdgeType.VALUE_FLOWS_TO
                    && edge.targetNodeId().equals(node.id()))
                .map(edge -> nodes.get(edge.sourceNodeId())).filter(Objects::nonNull).findFirst();
            Optional<String> path = value.flatMap(origin -> originPath(origin, visited));
            if (node.payload() instanceof FactNodePayload.FieldAccessPayload field
                    && field.rootExpressionKind().startsWith("INSTANCE_FIELD:"))
                return path.map(base -> append(base, field.fieldName()));
            return path;
        }
        return Optional.empty();
    }

    private String getterProperty(String methodName) {
        String stem = methodName.startsWith("get") && methodName.length() > 3 ? methodName.substring(3)
            : methodName.startsWith("is") && methodName.length() > 2 ? methodName.substring(2) : null;
        return stem == null ? null : Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
    }

    private String append(String base, String property) {
        return "$".equals(base) ? "$." + property : base + "." + property;
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
                                   RuleEffect effect, TargetResolutionStatus targetStatus,
                                   NormalizedConstraint constraint, double confidence, List<EvidenceRef> evidence) {
        return resolved(predicate, ruleId, category, effect, targetStatus, constraint, confidence, evidence,
            predicate.diagnostics());
    }

    BusinessRuleCandidate resolved(PredicateCandidate predicate, String ruleId, BusinessRuleCategory category,
                                   RuleEffect effect, TargetResolutionStatus targetStatus,
                                   NormalizedConstraint constraint, double confidence, List<EvidenceRef> evidence,
                                   List<CandidateDiagnostic> diagnostics) {
        String fingerprint = evidence.stream().map(ref -> ref.nodeId() + ":" + ref.role()).sorted()
            .reduce((left, right) -> left + "|" + right).orElse(predicate.conditionNodeId());
        return candidates.create(predicate.candidateId(), ruleId, category, effect, predicate.extractionStatus(),
            SemanticStatus.RESOLVED, targetStatus, constraint, confidence, evidence,
            diagnostics, fingerprint);
    }
}
