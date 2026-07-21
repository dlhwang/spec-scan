package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class AuthorizationGuardClassifier implements SemanticConstraintClassifier {
    private final AuthorizationGuardRegistry registry;

    public AuthorizationGuardClassifier(AuthorizationGuardRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public String id() { return "authorization-guard"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        FactNode call = findCall(context, condition);
        if (call == null || call.typeResolution().status() != TypeResolutionStatus.RESOLVED) {
            return Optional.empty();
        }
        AuthorizationGuardDescriptor descriptor = registry.find(call.typeResolution().resolvedSignature())
            .orElse(null);
        Boolean failureResult = failureResult(condition, predicate.failurePolarity());
        if (descriptor == null || failureResult == null || descriptor.failureResult() != failureResult) {
            return Optional.empty();
        }
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            descriptor.targetPath(), descriptor.operator(), List.of(), call.id());
        return Optional.of(new SemanticConstraintMatch(constraint, predicate.resolutionQuality(), List.of()));
    }

    private FactNode findCall(SemanticContext context, FactNode condition) {
        if (condition == null) return null;
        if (condition.type() == FactNodeType.METHOD_CALL) return condition;
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(condition.id());
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (FactNode target : context.index().targets(current, FactEdgeType.OPERAND_OF)) {
                if (target.type() == FactNodeType.METHOD_CALL) return target;
                pending.addLast(target.id());
            }
        }
        return null;
    }

    private Boolean failureResult(FactNode condition, FailurePolarity polarity) {
        if (condition == null || polarity == FailurePolarity.UNKNOWN) return null;
        String operator = condition.payload() instanceof FactNodePayload.ConditionPayload payload
            ? payload.rootOperator() : "MethodCallExpr";
        boolean conditionValue = polarity == FailurePolarity.WHEN_TRUE;
        return "!".equals(operator) ? !conditionValue : conditionValue;
    }
}
