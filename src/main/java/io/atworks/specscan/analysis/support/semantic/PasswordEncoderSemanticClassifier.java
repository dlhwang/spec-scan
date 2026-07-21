package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class PasswordEncoderSemanticClassifier implements SemanticConstraintClassifier {
    public String id() { return "spring-security-password-match"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        if (condition == null || !rejectsMismatch(context, condition, predicate.failurePolarity())) {
            return Optional.empty();
        }
        FactNode call = passwordMatchCall(context, condition.id());
        if (call == null) return Optional.empty();
        List<FactNode> arguments = context.index().targets(call.id(), FactEdgeType.OPERAND_OF, "ARGUMENT");
        if (arguments.size() < 2) return Optional.empty();
        Optional<String> inputPath = new SemanticInputPathResolver(context.index()).resolve(arguments.get(0));
        List<CandidateDiagnostic> diagnostics = new ArrayList<>(predicate.diagnostics());
        ResolutionQuality quality = predicate.resolutionQuality();
        if (inputPath.isEmpty()) {
            quality = ResolutionQuality.PARTIAL;
            diagnostics.add(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
                "PASSWORD_INPUT_ORIGIN_UNRESOLVED",
                "Password input origin could not be linked to an API parameter", call.id()));
        }
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            inputPath.orElse(null), "PASSWORD_MATCH", List.of(), arguments.get(1).id());
        return Optional.of(new SemanticConstraintMatch(constraint, quality, diagnostics));
    }

    private FactNode passwordMatchCall(SemanticContext context, String root) {
        Deque<TraversalStep> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(new TraversalStep(root, 0));
        while (!pending.isEmpty()) {
            TraversalStep step = pending.removeFirst();
            if (!visited.add(step.nodeId())) continue;
            FactNode node = context.index().node(step.nodeId());
            if (node != null && node.type() == FactNodeType.METHOD_CALL
                    && SpringSecurityPasswordMatcher.matches(context.index(), node)) return node;
            context.index().targets(step.nodeId(), FactEdgeType.OPERAND_OF)
                .forEach(target -> pending.addLast(new TraversalStep(target.id(), step.callDepth())));
            if (step.callDepth() < 2) {
                context.index().targets(step.nodeId(), FactEdgeType.CALLS, "TARGET")
                    .forEach(target -> pending.addLast(new TraversalStep(target.id(), step.callDepth() + 1)));
                context.index().targets(step.nodeId(), FactEdgeType.RETURNS)
                    .forEach(target -> pending.addLast(new TraversalStep(target.id(), step.callDepth())));
            }
        }
        return null;
    }

    private record TraversalStep(String nodeId, int callDepth) {}

    private boolean rejectsMismatch(SemanticContext context, FactNode condition, FailurePolarity polarity) {
        if (!(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) return false;
        if ("MethodCallExpr".equals(payload.rootOperator())) return polarity == FailurePolarity.WHEN_FALSE;
        if ("!".equals(payload.rootOperator())) return polarity == FailurePolarity.WHEN_TRUE;
        if (!Set.of("==", "!=").contains(payload.rootOperator())) return false;
        String booleanValue = context.index().targets(condition.id(), FactEdgeType.OPERAND_OF).stream()
            .filter(node -> node.payload() instanceof FactNodePayload.LiteralPayload literal
                && "BooleanLiteralExpr".equals(literal.literalKind()))
            .map(node -> ((FactNodePayload.LiteralPayload) node.payload()).value())
            .findFirst().orElse(null);
        if (booleanValue == null) return false;
        boolean expressionTrueOnMismatch = "==".equals(payload.rootOperator())
            ? "false".equals(booleanValue) : "true".equals(booleanValue);
        return expressionTrueOnMismatch
            ? polarity == FailurePolarity.WHEN_TRUE : polarity == FailurePolarity.WHEN_FALSE;
    }
}
