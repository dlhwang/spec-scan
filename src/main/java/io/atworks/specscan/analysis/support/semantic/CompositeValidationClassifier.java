package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class CompositeValidationClassifier {
    public static final String RULE_ID = "JAVA_CONDITIONAL_VALIDATION_GUARD";
    private final BinaryConstraintClassifier binary = new BinaryConstraintClassifier();

    public Optional<CompositeConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        FactNode root = context.index().node(predicate.source().conditionNodeId());
        if (predicate.failurePolarity() != FailurePolarity.WHEN_TRUE) {
            return Optional.empty();
        }
        List<NormalizedConstraint> guards = new ArrayList<>();
        List<SemanticConstraintMatch> requirements = new ArrayList<>();
        if (isSwitchEntry(root)) {
            NormalizedConstraint guard = enumGuard(context, root);
            if (guard != null) guards.add(guard);
            context.index().targets(root.id(), FactEdgeType.THEN_OUTCOME).stream()
                .filter(node -> node.type() == FactNodeType.RETURN)
                .flatMap(node -> context.index().targets(node.id(), FactEdgeType.OPERAND_OF).stream())
                .forEach(node -> collect(context, predicate, node, guards, requirements));
        } else if (isOperator(root, "&&")) {
            collect(context, predicate, root, guards, requirements);
        } else {
            return Optional.empty();
        }
        if (guards.isEmpty() || requirements.isEmpty()) return Optional.empty();
        ResolutionQuality quality = requirements.stream()
            .anyMatch(match -> match.resolutionQuality() == ResolutionQuality.PARTIAL)
            ? ResolutionQuality.PARTIAL : predicate.resolutionQuality();
        return Optional.of(new CompositeConstraintMatch(guards, requirements, quality,
            predicate.diagnostics()));
    }

    private void collect(SemanticContext context, SemanticPredicate source, FactNode node,
                         List<NormalizedConstraint> guards,
                         List<SemanticConstraintMatch> requirements) {
        if (isOperator(node, "&&") || isOperator(node, "||")) {
            context.index().targets(node.id(), FactEdgeType.OPERAND_OF)
                .forEach(child -> collect(context, source, child, guards, requirements));
            return;
        }
        NormalizedConstraint enumGuard = enumGuard(context, node);
        if (enumGuard != null) {
            guards.add(enumGuard);
            return;
        }
        PredicateCandidate childSource = new PredicateCandidate(source.source().candidateId() + ":" + node.id(),
            source.source().graphId(), node.id(), source.source().predicateType(),
            source.source().extractionStatus(), source.source().evidence(), source.source().diagnostics());
        SemanticPredicate child = new SemanticPredicate(childSource,
            new SemanticExpression(node.id(), operator(node), List.of()), FailurePolarity.WHEN_TRUE,
            null, null, source.evidence(), source.resolutionQuality(), source.diagnostics());
        Optional<SemanticConstraintMatch> classified = binary.classify(context, child);
        if (classified.isPresent()) {
            requirements.add(classified.get());
            return;
        }
        unresolvedNumericRequirement(context, node, source).ifPresent(requirements::add);
    }

    private Optional<SemanticConstraintMatch> unresolvedNumericRequirement(SemanticContext context,
                                                                            FactNode node,
                                                                            SemanticPredicate source) {
        String sourceOperator = operator(node);
        if (!Set.of("<", "<=", ">", ">=").contains(sourceOperator)) return Optional.empty();
        FactNode literal = context.index().targets(node.id(), FactEdgeType.OPERAND_OF).stream()
            .filter(value -> value.payload() instanceof FactNodePayload.LiteralPayload)
            .findFirst().orElse(null);
        if (literal == null) return Optional.empty();
        String value = ((FactNodePayload.LiteralPayload) literal.payload()).value();
        String requiredOperator = switch (sourceOperator) {
            case "<" -> "GTE"; case "<=" -> "GT"; case ">" -> "LTE"; case ">=" -> "LT";
            default -> throw new IllegalStateException();
        };
        CandidateDiagnostic diagnostic = new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
            "DELEGATED_ARGUMENT_MAPPING_PARTIAL",
            "Delegated numeric constraint target could not be mapped to an API input", node.id());
        return Optional.of(new SemanticConstraintMatch(new NormalizedConstraint(
            ConstraintKind.INPUT_LITERAL, null, requiredOperator, List.of(value), node.id()),
            ResolutionQuality.PARTIAL, List.of(diagnostic)));
    }

    private NormalizedConstraint enumGuard(SemanticContext context, FactNode node) {
        if (!isOperator(node, "==")) return null;
        List<FactNode> operands = context.index().targets(node.id(), FactEdgeType.OPERAND_OF);
        if (operands.size() != 2) return null;
        FactNode constant = operands.stream().filter(value -> value.type() == FactNodeType.ENUM_CONSTANT)
            .findFirst().orElse(null);
        FactNode value = operands.stream().filter(item -> item.type() != FactNodeType.ENUM_CONSTANT)
            .findFirst().orElse(null);
        if (constant == null || value == null) return null;
        Optional<String> path = new SemanticInputPathResolver(context.index()).resolve(value);
        FactNodePayload.EnumConstantPayload enumValue =
            (FactNodePayload.EnumConstantPayload) constant.payload();
        return new NormalizedConstraint(ConstraintKind.INPUT_LITERAL, path.orElse(null), "EQ",
            List.of(enumValue.constantName()), constant.id());
    }

    private boolean isOperator(FactNode node, String expected) {
        return node != null && node.payload() instanceof FactNodePayload.ConditionPayload condition
            && expected.equals(condition.rootOperator());
    }

    private boolean isSwitchEntry(FactNode node) {
        return node != null && node.payload() instanceof FactNodePayload.ConditionPayload condition
            && "SwitchEntry".equals(condition.astKind());
    }

    private String operator(FactNode node) {
        return node.payload() instanceof FactNodePayload.ConditionPayload condition
            ? condition.rootOperator() : null;
    }
}
