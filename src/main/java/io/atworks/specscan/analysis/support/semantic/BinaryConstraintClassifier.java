package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class BinaryConstraintClassifier implements SemanticConstraintClassifier {
    public String id() { return "binary-constraint"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) {
            return Optional.empty();
        }
        String operator = payload.rootOperator();
        if (!Set.of("==", "!=", "<", "<=", ">", ">=").contains(operator)) return Optional.empty();
        List<FactNode> operands = context.index().targets(condition.id(), FactEdgeType.OPERAND_OF);
        if (operands.size() != 2 || predicate.failurePolarity() == FailurePolarity.UNKNOWN) return Optional.empty();
        FactNode left = operands.get(0), right = operands.get(1);
        FactNode value = left, expected = right;
        String normalizedSourceOperator = operator;
        if (isConstant(left) && !isConstant(right)) {
            value = right; expected = left; normalizedSourceOperator = reverse(operator);
        }
        Optional<String> path = new SemanticInputPathResolver(context.index()).resolve(value);
        if (path.isEmpty()) return Optional.empty();
        String validOperator = predicate.failurePolarity() == FailurePolarity.WHEN_TRUE
            ? negate(normalizedSourceOperator) : normalize(normalizedSourceOperator);
        if (expected.type() == FactNodeType.NULL_LITERAL) {
            boolean rejectsNull = "==".equals(normalizedSourceOperator)
                    && predicate.failurePolarity() == FailurePolarity.WHEN_TRUE
                || "!=".equals(normalizedSourceOperator)
                    && predicate.failurePolarity() == FailurePolarity.WHEN_FALSE;
            if (!rejectsNull) return Optional.empty();
            return Optional.of(match(path.get(), "NOT_NULL", List.of(), condition.id(), predicate));
        }
        if (!(expected.payload() instanceof FactNodePayload.LiteralPayload literal)) return Optional.empty();
        return Optional.of(match(path.get(), validOperator, List.of(literal.value()), condition.id(), predicate));
    }

    private SemanticConstraintMatch match(String path, String operator, List<String> values,
                                           String source, SemanticPredicate predicate) {
        ResolutionQuality quality = predicate.resolutionQuality();
        return new SemanticConstraintMatch(new NormalizedConstraint(ConstraintKind.INPUT_LITERAL,
            path, operator, values, source), quality, List.of());
    }

    private boolean isConstant(FactNode node) {
        return node.type() == FactNodeType.NULL_LITERAL || node.type() == FactNodeType.LITERAL
            || node.type() == FactNodeType.ENUM_CONSTANT;
    }

    private String reverse(String operator) {
        return switch (operator) {
            case "<" -> ">"; case "<=" -> ">="; case ">" -> "<"; case ">=" -> "<=";
            default -> operator;
        };
    }

    private String negate(String operator) {
        return switch (operator) {
            case "==" -> "NEQ"; case "!=" -> "EQ"; case "<" -> "GTE"; case "<=" -> "GT";
            case ">" -> "LTE"; case ">=" -> "LT"; default -> operator;
        };
    }

    private String normalize(String operator) {
        return switch (operator) {
            case "==" -> "EQ"; case "!=" -> "NEQ"; case "<" -> "LT"; case "<=" -> "LTE";
            case ">" -> "GT"; case ">=" -> "GTE"; default -> operator;
        };
    }
}
