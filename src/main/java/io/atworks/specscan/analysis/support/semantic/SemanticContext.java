package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.PredicateCandidate;
import io.atworks.specscan.analysis.domain.candidate.CandidateDiagnostic;
import io.atworks.specscan.analysis.domain.candidate.CandidateDiagnosticSeverity;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class SemanticContext {
    private final FactGraphIndex index;

    public SemanticContext(FactCodeGraph graph) {
        this.index = new FactGraphIndex(graph);
    }

    public FactGraphIndex index() { return index; }

    public SemanticPredicate normalize(PredicateCandidate predicate) {
        if (!index.graph().graphId().equals(predicate.graphId())) {
            throw new IllegalArgumentException("predicate belongs to another graph");
        }
        FactNode condition = index.node(predicate.conditionNodeId());
        if (condition == null) throw new IllegalArgumentException("predicate condition is missing");
        List<FactNode> expressionNodes = expressionNodes(condition.id());
        boolean unsupported = expressionNodes.stream().anyMatch(node -> !isSupportedExpressionNode(node));
        List<CandidateDiagnostic> diagnostics = unsupported ? List.of(new CandidateDiagnostic(
            CandidateDiagnosticSeverity.WARNING, "UNSUPPORTED_SEMANTIC_EXPRESSION",
            "Expression contains a node outside the semantic MVP scope", condition.id())) : List.of();
        return new SemanticPredicate(predicate, expression(condition, new HashSet<>()),
            failurePolarity(condition.id(), predicate), null, methodSignature(condition.id()), predicate.evidence(),
            resolutionQuality(expressionNodes, unsupported), diagnostics);
    }

    private SemanticExpression expression(FactNode node, Set<String> visited) {
        if (!visited.add(node.id())) return new SemanticExpression(node.id(), "CYCLE", List.of());
        String operator = node.payload() instanceof FactNodePayload.ConditionPayload condition
            ? condition.rootOperator() : null;
        List<SemanticExpression> operands = index.targets(node.id(), FactEdgeType.OPERAND_OF).stream()
            .map(target -> expression(target, new HashSet<>(visited))).toList();
        return new SemanticExpression(node.id(), operator, operands);
    }

    private FailurePolarity failurePolarity(String conditionId, PredicateCandidate predicate) {
        Set<String> failureNodes = predicate.evidence().stream()
            .filter(evidence -> evidence.role() == io.atworks.specscan.analysis.domain.candidate.EvidenceRole.FAILURE_OUTCOME)
            .map(io.atworks.specscan.analysis.domain.candidate.EvidenceRef::nodeId)
            .collect(java.util.stream.Collectors.toSet());
        boolean thenFailure = index.outgoing(conditionId).stream()
            .anyMatch(edge -> edge.type() == FactEdgeType.THEN_OUTCOME
                && (failureNodes.isEmpty() || failureNodes.contains(edge.targetNodeId())));
        boolean elseFailure = index.outgoing(conditionId).stream()
            .anyMatch(edge -> edge.type() == FactEdgeType.ELSE_OUTCOME
                && (failureNodes.isEmpty() || failureNodes.contains(edge.targetNodeId())));
        if (!failureNodes.isEmpty() && !thenFailure && !elseFailure) {
            thenFailure = index.outgoing(conditionId).stream()
                .anyMatch(edge -> edge.type() == FactEdgeType.THEN_OUTCOME);
            elseFailure = index.outgoing(conditionId).stream()
                .anyMatch(edge -> edge.type() == FactEdgeType.ELSE_OUTCOME);
        }
        if (thenFailure && !elseFailure) return FailurePolarity.WHEN_TRUE;
        if (elseFailure && !thenFailure) return FailurePolarity.WHEN_FALSE;
        return FailurePolarity.UNKNOWN;
    }

    private String methodSignature(String conditionId) {
        return index.targets(conditionId, FactEdgeType.OPERAND_OF).stream()
            .filter(node -> node.type() == FactNodeType.METHOD_CALL)
            .map(FactNode::typeResolution).map(TypeResolution::resolvedSignature)
            .filter(Objects::nonNull).findFirst().orElse(null);
    }

    private ResolutionQuality resolutionQuality(List<FactNode> expressionNodes, boolean unsupported) {
        boolean unresolved = expressionNodes.stream()
            .anyMatch(node -> node.typeResolution().status() == TypeResolutionStatus.UNRESOLVED);
        return unresolved || unsupported ? ResolutionQuality.PARTIAL : ResolutionQuality.RESOLVED;
    }

    private boolean isSupportedExpressionNode(FactNode node) {
        if (node.payload() instanceof FactNodePayload.ConditionPayload condition) {
            return Set.of("&&", "||", "==", "!=", "<", "<=", ">", ">=", "!",
                "MethodCallExpr").contains(condition.rootOperator());
        }
        return switch (node.type()) {
            case METHOD_CALL, FIELD_ACCESS, VALUE_FIELD, PARAMETER, LOCAL_VARIABLE,
                 NULL_LITERAL, LITERAL, ENUM_CONSTANT -> true;
            default -> false;
        };
    }

    private List<FactNode> expressionNodes(String root) {
        List<FactNode> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (FactNode target : index.targets(current, FactEdgeType.OPERAND_OF)) {
                result.add(target); pending.addLast(target.id());
            }
        }
        return result;
    }
}
