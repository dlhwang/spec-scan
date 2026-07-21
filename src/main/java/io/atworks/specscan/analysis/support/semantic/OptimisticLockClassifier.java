package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class OptimisticLockClassifier implements SemanticConstraintClassifier {
    private final InputDomainEqualityClassifier equality = new InputDomainEqualityClassifier();

    public String id() { return "optimistic-lock"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        Optional<SemanticConstraintMatch> base = equality.classify(context, predicate);
        if (base.isEmpty() || !"EQUALS".equals(base.get().constraint().operator())) return Optional.empty();
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        if (condition == null) return Optional.empty();
        List<FactNode> operands = context.index().targets(condition.id(), FactEdgeType.OPERAND_OF);
        if (operands.size() != 2) return Optional.empty();
        SemanticInputPathResolver paths = new SemanticInputPathResolver(context.index());
        Optional<String> left = paths.resolve(operands.get(0));
        Optional<String> right = paths.resolve(operands.get(1));
        if (left.isPresent() == right.isPresent()) return Optional.empty();
        FactNode input = left.isPresent() ? operands.get(0) : operands.get(1);
        FactNode domain = left.isPresent() ? operands.get(1) : operands.get(0);
        FactNode versionField = annotatedVersionField(context, domain);
        if (versionField == null) return Optional.empty();
        String path = base.get().constraint().targetPath();
        if ("$".equals(path)) path = requestParameterPath(context, input).orElse(path);
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.INPUT_TO_DOMAIN,
            path, "OPTIMISTIC_LOCK_MATCH", List.of(), versionField.id());
        return Optional.of(new SemanticConstraintMatch(constraint,
            base.get().resolutionQuality(), base.get().diagnostics()));
    }

    private FactNode annotatedVersionField(SemanticContext context, FactNode root) {
        Deque<String> pending = new ArrayDeque<>(); Set<String> visited = new HashSet<>(); pending.add(root.id());
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            FactNode node = context.index().node(current);
            if (node != null && node.type() == FactNodeType.VALUE_FIELD && hasVersionAnnotation(context, node)) {
                return node;
            }
            for (FactEdge edge : context.index().outgoing(current)) {
                if (Set.of(FactEdgeType.READS, FactEdgeType.CALLS, FactEdgeType.RETURNS,
                        FactEdgeType.OPERAND_OF).contains(edge.type())) pending.addLast(edge.targetNodeId());
            }
        }
        return null;
    }

    private boolean hasVersionAnnotation(SemanticContext context, FactNode field) {
        return context.index().targets(field.id(), FactEdgeType.HAS_ANNOTATION).stream()
            .filter(node -> node.payload() instanceof FactNodePayload.AnnotationPayload)
            .map(node -> (FactNodePayload.AnnotationPayload) node.payload())
            .anyMatch(annotation -> "Version".equals(annotation.annotationType())
                || annotation.annotationType().endsWith(".Version"));
    }

    private Optional<String> requestParameterPath(SemanticContext context, FactNode root) {
        Deque<String> pending = new ArrayDeque<>(); Set<String> visited = new HashSet<>(); pending.add(root.id());
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            FactNode node = context.index().node(current);
            if (node != null && node.type() == FactNodeType.PARAMETER
                    && node.payload() instanceof FactNodePayload.ParameterPayload parameter) {
                return Optional.of("$." + parameter.name());
            }
            context.index().outgoing(current).stream()
                .filter(edge -> edge.type() == FactEdgeType.READS
                    || edge.type() == FactEdgeType.ORIGINATES_FROM)
                .forEach(edge -> pending.addLast(edge.targetNodeId()));
        }
        return Optional.empty();
    }
}
