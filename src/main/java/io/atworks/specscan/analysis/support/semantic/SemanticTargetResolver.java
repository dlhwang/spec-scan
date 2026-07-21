package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class SemanticTargetResolver {
    public SemanticTargetResolution resolve(SemanticContext context, FactNode value) {
        FactNode responseField = responseField(context, value);
        if (responseField != null) {
            String fieldName = responseField.payload() instanceof FactNodePayload.FieldAccessPayload field
                ? field.fieldName() : responseField.snippet();
            return new SemanticTargetResolution(SemanticTargetRole.RESPONSE,
                "$." + fieldName, responseField.id());
        }
        Optional<String> requestPath = new SemanticInputPathResolver(context.index()).resolve(value);
        return requestPath.map(path -> new SemanticTargetResolution(
                SemanticTargetRole.REQUEST, path, value.id()))
            .orElseGet(() -> new SemanticTargetResolution(
                SemanticTargetRole.DOMAIN, null, value.id()));
    }

    private FactNode responseField(SemanticContext context, FactNode value) {
        Set<String> responseTypes = context.index().targets(context.index().graph().apiMethodNodeId(),
                FactEdgeType.RETURNS_TYPE).stream()
            .filter(node -> node.payload() instanceof FactNodePayload.TypePayload)
            .map(node -> (FactNodePayload.TypePayload) node.payload())
            .flatMap(type -> java.util.stream.Stream.of(type.declaredType(), type.qualifiedType()))
            .filter(Objects::nonNull).map(this::rawType).collect(java.util.stream.Collectors.toSet());
        if (responseTypes.isEmpty()) return null;
        Deque<String> pending = new ArrayDeque<>(); Set<String> visited = new HashSet<>();
        pending.add(value.id());
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            FactNode node = context.index().node(current);
            if (isResponseField(node, responseTypes)) return node;
            for (FactEdge edge : context.index().outgoing(current)) {
                if (edge.type() == FactEdgeType.READS || edge.type() == FactEdgeType.VALUE_FLOWS_TO
                        || edge.type() == FactEdgeType.ORIGINATES_FROM
                            && "CALL_ARGUMENT".equals(edge.role())) {
                    pending.addLast(edge.targetNodeId());
                }
            }
        }
        return null;
    }

    private boolean isResponseField(FactNode node, Set<String> responseTypes) {
        if (node == null || node.type() != FactNodeType.VALUE_FIELD
                || !(node.payload() instanceof FactNodePayload.FieldAccessPayload field)
                || !field.rootExpressionKind().startsWith("CONSTRUCTOR_PARAMETER:")) return false;
        String owner = rawType(field.rootExpressionKind().substring("CONSTRUCTOR_PARAMETER:".length()));
        return responseTypes.stream().anyMatch(type -> type.equals(owner)
            || simpleName(type).equals(simpleName(owner)));
    }

    private String rawType(String type) {
        int generic = type.indexOf('<');
        return generic < 0 ? type : type.substring(0, generic);
    }

    private String simpleName(String type) {
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }
}
