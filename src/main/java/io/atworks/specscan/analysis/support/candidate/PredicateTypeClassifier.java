package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.PredicateType;
import io.atworks.specscan.analysis.domain.fact.*;
import java.util.HashSet;
import java.util.Set;

public final class PredicateTypeClassifier {
    public PredicateType classify(FactCodeGraph graph, FactNode condition) {
        if (condition.type() != FactNodeType.CONDITION
            || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) {
            throw new IllegalArgumentException("condition fact node is required");
        }
        String operator = payload.rootOperator();
        if ("&&".equals(operator) || "||".equals(operator)) return PredicateType.COMPOSITE;

        Set<FactNodeType> operandTypes = new HashSet<>();
        for (FactEdge edge : graph.edges()) {
            if (edge.type() == FactEdgeType.OPERAND_OF
                    && (edge.sourceNodeId().equals(condition.id()) || edge.targetNodeId().equals(condition.id()))) {
                String operandId = edge.sourceNodeId().equals(condition.id()) ? edge.targetNodeId() : edge.sourceNodeId();
                graph.nodes().stream().filter(node -> node.id().equals(operandId))
                    .findFirst().ifPresent(node -> operandTypes.add(node.type()));
            }
        }
        if (operandTypes.contains(FactNodeType.ENUM_CONSTANT)) return PredicateType.ENUM_COMPARISON;
        if (operandTypes.contains(FactNodeType.NULL_LITERAL)) return PredicateType.NULL_CHECK;
        if (isComparison(operator)) return PredicateType.COMPARISON;
        if (operandTypes.contains(FactNodeType.METHOD_CALL)) return PredicateType.BOOLEAN_CALL;
        return PredicateType.UNKNOWN;
    }

    private boolean isComparison(String operator) {
        return "==".equals(operator) || "!=".equals(operator) || ">".equals(operator)
            || ">=".equals(operator) || "<".equals(operator) || "<=".equals(operator);
    }
}
