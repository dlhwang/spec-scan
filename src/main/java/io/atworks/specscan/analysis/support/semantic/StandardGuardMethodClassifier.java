package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class StandardGuardMethodClassifier implements SemanticConstraintClassifier {
    private final StandardGuardMethodRegistry registry;

    public StandardGuardMethodClassifier(StandardGuardMethodRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public String id() { return "standard-guard-method"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        if (condition == null) return Optional.empty();
        FactNode call = condition.type() == FactNodeType.METHOD_CALL ? condition
            : methodCall(context, condition.id());
        if (call == null || call.typeResolution().status() != TypeResolutionStatus.RESOLVED) {
            return Optional.empty();
        }
        StandardGuardMethodDescriptor descriptor = registry.find(call.typeResolution().resolvedSignature())
            .orElse(null);
        if (descriptor == null || descriptor.failureSemantics() == GuardFailureSemantics.RETURNS_TRUE_WHEN_INVALID
                && predicate.failurePolarity() != FailurePolarity.WHEN_TRUE) return Optional.empty();
        List<FactNode> arguments = context.index().targets(call.id(), FactEdgeType.OPERAND_OF, "ARGUMENT");
        if (descriptor.argumentIndex() >= arguments.size()) return Optional.empty();
        FactNode value = arguments.get(descriptor.argumentIndex());
        Optional<String> path = new SemanticInputPathResolver(context.index()).resolve(value);
        if (path.isEmpty()) return Optional.empty();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.INPUT_LITERAL,
            path.get(), descriptor.normalizedOperator(), List.of(), call.id());
        return Optional.of(new SemanticConstraintMatch(constraint, predicate.resolutionQuality(), List.of()));
    }

    private FactNode methodCall(SemanticContext context, String root) {
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(root);
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
}
