package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class InputDomainEqualityClassifier implements SemanticConstraintClassifier {
    public String id() { return "input-domain-equality"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)
                || !Set.of("==", "!=").contains(payload.rootOperator())
                || predicate.failurePolarity() == FailurePolarity.UNKNOWN) return Optional.empty();
        List<FactNode> operands = context.index().targets(condition.id(), FactEdgeType.OPERAND_OF);
        if (operands.size() != 2 || isConstant(operands.get(0)) || isConstant(operands.get(1))) {
            return Optional.empty();
        }
        SemanticInputPathResolver resolver = new SemanticInputPathResolver(context.index());
        Optional<String> leftPath = resolver.resolve(operands.get(0));
        Optional<String> rightPath = resolver.resolve(operands.get(1));
        if (leftPath.isPresent() == rightPath.isPresent()) return Optional.empty();
        FactNode domain = leftPath.isPresent() ? operands.get(1) : operands.get(0);
        String targetPath = leftPath.orElseGet(rightPath::orElseThrow);
        boolean sourceEquality = "==".equals(payload.rootOperator());
        boolean validEquality = predicate.failurePolarity() == FailurePolarity.WHEN_TRUE
            ? !sourceEquality : sourceEquality;
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.INPUT_TO_DOMAIN,
            targetPath, validEquality ? "EQUALS" : "NOT_EQUALS", List.of(), domain.id());
        return Optional.of(new SemanticConstraintMatch(constraint, predicate.resolutionQuality(),
            predicate.diagnostics()));
    }

    private boolean isConstant(FactNode node) {
        return node.type() == FactNodeType.NULL_LITERAL || node.type() == FactNodeType.LITERAL
            || node.type() == FactNodeType.ENUM_CONSTANT;
    }
}
