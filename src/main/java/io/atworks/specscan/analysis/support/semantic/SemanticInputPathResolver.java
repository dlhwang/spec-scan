package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.fact.*;
import java.util.*;

final class SemanticInputPathResolver {
    private final FactGraphIndex index;

    SemanticInputPathResolver(FactGraphIndex index) { this.index = index; }

    Optional<String> resolve(FactNode node) { return resolve(node, new HashSet<>()); }

    private Optional<String> resolve(FactNode node, Set<String> visited) {
        if (node == null || !visited.add(node.id())) return Optional.empty();
        if (node.type() == FactNodeType.PARAMETER) {
            Optional<FactEdge> origin = index.outgoing(node.id()).stream()
                .filter(edge -> edge.type() == FactEdgeType.ORIGINATES_FROM
                    && ("CALL_ARGUMENT".equals(edge.role()) || "COLLECTION_ELEMENT".equals(edge.role())))
                .findFirst();
            if (origin.isEmpty()) return rootParameterPath(node);
            Optional<String> path = resolve(index.node(origin.get().targetNodeId()), visited);
            return "COLLECTION_ELEMENT".equals(origin.get().role())
                ? path.map(value -> value + "[*]") : path;
        }
        if (node.payload() instanceof FactNodePayload.MethodCallPayload call) {
            FactNode receiver = index.targets(node.id(), FactEdgeType.OPERAND_OF, "RECEIVER").stream()
                .findFirst().orElse(null);
            Optional<String> base = resolve(receiver, visited);
            String property = getterProperty(call.methodName());
            if ("get".equals(call.methodName()) && call.argumentCount() == 1)
                return base.map(value -> value + "[*]");
            if (property == null) property = recordAccessorProperty(node, call);
            if (property == null) property = uniqueSchemaAccessorProperty(call);
            String resolvedProperty = property;
            return resolvedProperty == null ? base : base.map(value -> append(value, resolvedProperty));
        }
        FactNode declaration = index.targets(node.id(), FactEdgeType.READS).stream().findFirst().orElse(null);
        if (declaration != null) return resolve(declaration, visited);
        if (node.type() == FactNodeType.LOCAL_VARIABLE) {
            FactNode assigned = index.targets(node.id(), FactEdgeType.ASSIGNED_FROM).stream()
                .findFirst().orElse(null);
            return resolve(assigned, visited);
        }
        if (node.type() == FactNodeType.VALUE_FIELD) {
            FactNode value = index.incoming(node.id()).stream()
                .filter(edge -> edge.type() == FactEdgeType.VALUE_FLOWS_TO)
                .map(edge -> index.node(edge.sourceNodeId())).filter(Objects::nonNull)
                .findFirst().orElse(null);
            Optional<String> path = resolve(value, visited);
            if (node.payload() instanceof FactNodePayload.FieldAccessPayload field
                    && field.rootExpressionKind().startsWith("INSTANCE_FIELD:"))
                return path.map(base -> append(base, field.fieldName()));
            return path;
        }
        return Optional.empty();
    }

    private Optional<String> rootParameterPath(FactNode node) {
        if (!(node.payload() instanceof FactNodePayload.ParameterPayload parameter))
            return Optional.of("$");
        return Set.of("PATH", "QUERY", "HEADER").contains(parameter.bindingLocation())
            ? Optional.of("$." + parameter.bindingName()) : Optional.of("$");
    }

    private String getterProperty(String methodName) {
        String stem = methodName.startsWith("get") && methodName.length() > 3 ? methodName.substring(3)
            : methodName.startsWith("is") && methodName.length() > 2 ? methodName.substring(2) : null;
        return stem == null ? null : Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
    }

    private String recordAccessorProperty(FactNode callNode, FactNodePayload.MethodCallPayload call) {
        if (call.argumentCount() != 0 || callNode.typeResolution().status() != TypeResolutionStatus.RESOLVED)
            return null;
        String signature = callNode.typeResolution().resolvedSignature();
        if (signature == null) return null;
        int boundary = signature.lastIndexOf('.' + call.methodName() + "(");
        if (boundary < 0) return null;
        String declaringType = signature.substring(0, boundary);
        for (FactNode type : index.graph().nodes()) {
            if (!(type.payload() instanceof FactNodePayload.TypePayload payload)
                    || payload.qualifiedType() == null || !declaringType.equals(payload.qualifiedType())) continue;
            boolean declared = index.targets(type.id(), FactEdgeType.HAS_FIELD).stream()
                .anyMatch(field -> field.payload() instanceof FactNodePayload.SchemaFieldPayload schema
                    && schema.javaName().equals(call.methodName()));
            if (declared) return call.methodName();
        }
        return null;
    }

    private String uniqueSchemaAccessorProperty(FactNodePayload.MethodCallPayload call) {
        if (call.argumentCount() != 0) return null;
        Set<String> jsonNames = new HashSet<>();
        index.graph().nodes().stream()
            .filter(node -> node.payload() instanceof FactNodePayload.SchemaFieldPayload schema
                && schema.javaName().equals(call.methodName()))
            .map(node -> ((FactNodePayload.SchemaFieldPayload) node.payload()).jsonName())
            .forEach(jsonNames::add);
        return jsonNames.size() == 1 ? jsonNames.iterator().next() : null;
    }

    private String append(String base, String property) {
        return "$".equals(base) ? "$." + property : base + "." + property;
    }
}
