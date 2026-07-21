package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import io.atworks.specscan.analysis.domain.fact.FactEdge;
import io.atworks.specscan.analysis.domain.fact.FactEdgeType;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.FactNodePayload;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.fact.SourceRange;
import io.atworks.specscan.analysis.domain.fact.TypeResolution;
import io.atworks.specscan.analysis.support.TypeResolver;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class DtoSchemaGraphBuilder {
    private static final int MAX_SCHEMA_DEPTH = 8;
    private static final Set<String> COLLECTION_TYPES = Set.of(
        "Collection", "Iterable", "List", "Set", "Queue", "Deque");

    private final DeterministicFactNodeIdGenerator ids;

    DtoSchemaGraphBuilder(DeterministicFactNodeIdGenerator ids) {
        this.ids = ids;
    }

    void expandApiTypes(MethodDeclaration method, FactNode methodNode, String owner, Path workspace,
        FactGraphAccumulator acc, TypeResolver resolver) {
        for (int index = 0; index < method.getParameters().size(); index++) {
            int parameterIndex = index;
            var parameter = method.getParameter(index);
            FactNode parameterNode = acc.nodes().stream()
                .filter(node -> node.type() == FactNodeType.PARAMETER
                    && node.payload() instanceof FactNodePayload.ParameterPayload payload
                    && payload.index() == parameterIndex
                    && payload.name().equals(parameter.getNameAsString()))
                .findFirst().orElse(null);
            if (parameterNode == null) continue;
            FactNode declaredTypeNode = targetOf(parameterNode, FactEdgeType.HAS_TYPE, acc);
            if (declaredTypeNode == null) continue;
            expandType(parameter.getType(), declaredTypeNode, owner, workspace, acc, resolver,
                new LinkedHashSet<>(), 0);
        }
        if (!method.getType().isVoidType()) {
            Type responseType = responseBodyType(method.getType());
            SourceRange range = FactExpressionVisitor.range(method.getType(), workspace);
            String qualified = resolveType(responseType);
            FactNode responseTypeNode = new FactNode(ids.generate(FactNodeType.TYPE, owner, range,
                "api-response-type"), FactNodeType.TYPE, range, method.getTypeAsString(),
                qualified == null ? TypeResolution.unresolved("RETURN_TYPE_RESOLUTION_FAILED")
                    : TypeResolution.resolvedType(qualified),
                new FactNodePayload.TypePayload(responseType.asString(), qualified,
                    responseType.isPrimitiveType(), false, null));
            relate(methodNode, responseTypeNode, FactEdgeType.RETURNS_TYPE, 0,
                "RESPONSE_BODY_TYPE", acc);
            expandType(responseType, responseTypeNode, owner, workspace, acc, resolver,
                new LinkedHashSet<>(), 0);
        }
        linkGetterCalls(acc);
        linkFieldMappings(acc);
    }

    private void linkFieldMappings(FactGraphAccumulator acc) {
        for (FactEdge flow : acc.edges()) {
            if (flow.type() != FactEdgeType.VALUE_FLOWS_TO) continue;
            FactNode value = node(flow.sourceNodeId(), acc);
            FactNode target = node(flow.targetNodeId(), acc);
            if (value == null || target == null
                || (target.type() != FactNodeType.VALUE_FIELD
                    && target.type() != FactNodeType.SCHEMA_FIELD)) continue;
            FactNode sourceField = value.type() == FactNodeType.SCHEMA_FIELD ? value
                : acc.edges().stream().filter(read -> read.sourceNodeId().equals(value.id())
                        && read.type() == FactEdgeType.READS)
                    .map(FactEdge::targetNodeId).map(id -> node(id, acc))
                    .filter(java.util.Objects::nonNull)
                    .filter(candidate -> candidate.type() == FactNodeType.SCHEMA_FIELD)
                    .findFirst().orElse(null);
            if (sourceField != null) {
                relate(sourceField, target, FactEdgeType.MAPS_TO, flow.ordinal(),
                    "TRANSFORM_FIELD", acc);
            }
        }
    }

    private FactNode node(String id, FactGraphAccumulator acc) {
        return acc.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    private void linkGetterCalls(FactGraphAccumulator acc) {
        for (FactNode call : acc.nodes()) {
            if (call.type() != FactNodeType.METHOD_CALL
                || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)) continue;
            String propertyName = getterProperty(payload.methodName());
            String signature = call.typeResolution().resolvedSignature();
            if (propertyName == null || signature == null) continue;
            int methodBoundary = signature.lastIndexOf('.' + payload.methodName() + "(");
            if (methodBoundary < 0) continue;
            String declaringType = signature.substring(0, methodBoundary);
            acc.nodes().stream().filter(type -> type.type() == FactNodeType.TYPE
                    && declaringType.equals(type.typeResolution().qualifiedType()))
                .flatMap(type -> acc.edges().stream()
                    .filter(edge -> edge.sourceNodeId().equals(type.id())
                        && edge.type() == FactEdgeType.HAS_FIELD))
                .map(FactEdge::targetNodeId)
                .map(id -> acc.nodes().stream().filter(node -> node.id().equals(id))
                    .findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(field -> field.payload() instanceof FactNodePayload.SchemaFieldPayload fp
                    && fp.javaName().equals(propertyName))
                .findFirst().ifPresent(field -> relate(call, field, FactEdgeType.READS, -1,
                    "DTO_GETTER_FIELD", acc));
        }
    }

    private String getterProperty(String methodName) {
        String suffix;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            suffix = methodName.substring(3);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            suffix = methodName.substring(2);
        } else {
            return null;
        }
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private Type responseBodyType(Type declaredType) {
        if (!declaredType.isClassOrInterfaceType()) return declaredType;
        var classType = declaredType.asClassOrInterfaceType();
        if (!classType.getNameAsString().equals("ResponseEntity")) return declaredType;
        return classType.getTypeArguments().filter(arguments -> !arguments.isEmpty())
            .map(arguments -> arguments.get(0)).orElse(declaredType);
    }

    private void expandType(Type type, FactNode typeNode, String owner, Path workspace,
        FactGraphAccumulator acc, TypeResolver resolver, Set<String> path, int depth) {
        if (depth > MAX_SCHEMA_DEPTH) return;
        Type effectiveType = elementType(type).orElse(type);
        String qualified = resolveType(effectiveType);
        String lookup = qualified == null ? rawType(effectiveType) : eraseGenerics(qualified);
        if (lookup == null || lookup.startsWith("java.") || !path.add(lookup)) return;
        Optional<TypeDeclaration<?>> declaration = resolver.resolveAnyTypeDeclaration(lookup);
        if (declaration.isEmpty()) {
            path.remove(lookup);
            return;
        }
        TypeDeclaration<?> dto = declaration.get();
        Map<String, Type> substitutions = typeSubstitutions(effectiveType, dto);
        JsonNamingStrategy namingStrategy = jsonNamingStrategy(dto);
        if (dto instanceof ClassOrInterfaceDeclaration classDeclaration) {
            classDeclaration.getFields().stream().flatMap(field -> field.getVariables().stream())
                .forEach(field -> addField(dto, field, typeNode, owner, workspace, acc, resolver,
                    path, depth, substitutions, namingStrategy));
            classDeclaration.getExtendedTypes().forEach(parent ->
                expandInheritedType(substituteType(parent, substitutions), typeNode, owner,
                    workspace, acc, resolver, path, depth + 1));
        } else if (dto instanceof RecordDeclaration recordDeclaration) {
            recordDeclaration.getParameters().forEach(component -> {
                if (isJsonIgnored(component.getAnnotations())) return;
                SourceRange range = FactExpressionVisitor.range(component, workspace);
                addSchemaField(component.getNameAsString(), component.getType(),
                    substituteType(component.getType(), substitutions), null,
                    component.getAnnotations(), range, typeNode, owner, workspace, acc, resolver,
                    path, depth, namingStrategy);
            });
        } else if (dto instanceof EnumDeclaration enumDeclaration) {
            String enumQualifiedName = enumQualifiedName(enumDeclaration, lookup);
            enumDeclaration.getEntries().forEach(entry -> {
                SourceRange range = FactExpressionVisitor.range(entry, workspace);
                FactNode constant = new FactNode(ids.generate(FactNodeType.ENUM_CONSTANT, owner,
                    range, "enum-constant:" + entry.getNameAsString()),
                    FactNodeType.ENUM_CONSTANT, range, entry.getNameAsString(),
                    TypeResolution.resolvedType(enumQualifiedName),
                    new FactNodePayload.EnumConstantPayload(enumQualifiedName,
                        entry.getNameAsString()));
                relate(typeNode, constant, FactEdgeType.HAS_ENUM_CONSTANT, -1,
                    "ENUM_CONSTANT", acc);
            });
        }
        path.remove(lookup);
    }

    private String enumQualifiedName(EnumDeclaration declaration, String fallback) {
        try {
            return declaration.resolve().getQualifiedName();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void addField(TypeDeclaration<?> declaration, VariableDeclarator field,
        FactNode ownerType, String owner, Path workspace, FactGraphAccumulator acc,
        TypeResolver resolver, Set<String> path, int depth, Map<String, Type> substitutions,
        JsonNamingStrategy namingStrategy) {
        SourceRange range = FactExpressionVisitor.range(field, workspace);
        var annotations = field.findAncestor(com.github.javaparser.ast.body.FieldDeclaration.class)
            .map(com.github.javaparser.ast.body.FieldDeclaration::getAnnotations)
            .orElseGet(com.github.javaparser.ast.NodeList::new);
        if (isJsonIgnored(annotations)) return;
        addSchemaField(field.getNameAsString(), field.getType(),
            substituteType(field.getType(), substitutions),
            field.getInitializer().map(Expression::toString).orElse(null), annotations, range,
            ownerType, owner, workspace, acc, resolver, path, depth, namingStrategy);
    }

    private void addSchemaField(String javaName, Type declaredFieldType, Type fieldType,
        String defaultValue,
        com.github.javaparser.ast.NodeList<AnnotationExpr> annotations, SourceRange range,
        FactNode ownerType, String owner, Path workspace, FactGraphAccumulator acc,
        TypeResolver resolver, Set<String> path, int depth, JsonNamingStrategy namingStrategy) {
        String qualified = resolveType(fieldType);
        String jsonName = jsonName(javaName, annotations, namingStrategy);
        FactNode fieldNode = new FactNode(ids.generate(FactNodeType.SCHEMA_FIELD, owner, range,
            "schema-field:" + javaName), FactNodeType.SCHEMA_FIELD, range, javaName,
            qualified == null ? TypeResolution.unresolved("FIELD_TYPE_RESOLUTION_FAILED")
                : TypeResolution.resolvedType(qualified),
            new FactNodePayload.SchemaFieldPayload(javaName, jsonName, declaredFieldType.asString(),
                fieldType.isPrimitiveType(), defaultValue));
        relate(ownerType, fieldNode, FactEdgeType.HAS_FIELD, 0, "FIELD", acc);

        for (int index = 0; index < annotations.size(); index++) {
            AnnotationExpr annotation = annotations.get(index);
            SourceRange annotationRange = FactExpressionVisitor.range(annotation, workspace);
            FactNode annotationNode = new FactNode(ids.generate(FactNodeType.ANNOTATION, owner,
                annotationRange, "field-annotation:" + javaName + ":" + index),
                FactNodeType.ANNOTATION, annotationRange, annotation.toString(),
                TypeResolution.notApplicable(), new FactNodePayload.AnnotationPayload(
                    annotation.getNameAsString(), attributes(annotation)));
            relate(fieldNode, annotationNode, FactEdgeType.HAS_ANNOTATION, index,
                "FIELD_ANNOTATION", acc);
        }

        Type nestedType = elementType(fieldType).orElse(fieldType);
        String nestedQualified = resolveType(nestedType);
        FactNode nestedTypeNode = new FactNode(ids.generate(FactNodeType.TYPE, owner, range,
            "field-type:" + javaName), FactNodeType.TYPE, range, nestedType.asString(),
            nestedQualified == null ? TypeResolution.unresolved("FIELD_TYPE_RESOLUTION_FAILED")
                : TypeResolution.resolvedType(nestedQualified),
            new FactNodePayload.TypePayload(declaredFieldType.asString(), qualified,
                fieldType.isPrimitiveType(), elementType(fieldType).isPresent(),
                elementType(fieldType).map(Type::asString).orElse(null)));
        FactEdgeType edgeType = elementType(fieldType).isPresent()
            ? FactEdgeType.ELEMENT_TYPE : FactEdgeType.HAS_TYPE;
        relate(fieldNode, nestedTypeNode, edgeType, 0,
            edgeType == FactEdgeType.ELEMENT_TYPE ? "COLLECTION_ELEMENT" : "DECLARED_TYPE", acc);
        expandType(nestedType, nestedTypeNode, owner, workspace, acc, resolver, path, depth + 1);
    }

    private void expandInheritedType(Type parent, FactNode childType, String owner, Path workspace,
        FactGraphAccumulator acc, TypeResolver resolver, Set<String> path, int depth) {
        expandType(parent, childType, owner, workspace, acc, resolver, path, depth);
    }

    private Map<String, Type> typeSubstitutions(Type actualType, TypeDeclaration<?> declaration) {
        Map<String, Type> substitutions = new LinkedHashMap<>();
        if (!actualType.isClassOrInterfaceType()
            || !(declaration instanceof NodeWithTypeParameters<?> genericDeclaration)) {
            return substitutions;
        }
        var arguments = actualType.asClassOrInterfaceType().getTypeArguments().orElse(null);
        if (arguments == null) return substitutions;
        var parameters = genericDeclaration.getTypeParameters();
        for (int index = 0; index < Math.min(arguments.size(), parameters.size()); index++) {
            substitutions.put(parameters.get(index).getNameAsString(), arguments.get(index));
        }
        return substitutions;
    }

    private Type substituteType(Type declaredType, Map<String, Type> substitutions) {
        Type direct = substitutions.get(declaredType.asString());
        if (direct != null) return direct;
        if (!declaredType.isClassOrInterfaceType()) return declaredType;
        var copy = declaredType.asClassOrInterfaceType().clone();
        copy.getTypeArguments().ifPresent(arguments -> {
            for (int index = 0; index < arguments.size(); index++) {
                Type replacement = substitutions.get(arguments.get(index).asString());
                if (replacement != null) arguments.set(index, replacement.clone());
            }
        });
        return copy;
    }

    private FactNode targetOf(FactNode source, FactEdgeType type, FactGraphAccumulator acc) {
        return acc.edges().stream().filter(edge -> edge.sourceNodeId().equals(source.id())
                && edge.type() == type).findFirst()
            .flatMap(edge -> acc.nodes().stream()
                .filter(node -> node.id().equals(edge.targetNodeId())).findFirst())
            .orElse(null);
    }

    private Optional<Type> elementType(Type type) {
        if (type.isArrayType()) return Optional.of(type.asArrayType().getComponentType());
        if (!type.isClassOrInterfaceType()) return Optional.empty();
        var classType = type.asClassOrInterfaceType();
        if (!COLLECTION_TYPES.contains(classType.getNameAsString())) return Optional.empty();
        return classType.getTypeArguments().filter(arguments -> !arguments.isEmpty())
            .map(arguments -> arguments.get(0));
    }

    private String rawType(Type type) {
        return type.isClassOrInterfaceType()
            ? type.asClassOrInterfaceType().getNameWithScope() : type.asString();
    }

    private String resolveType(Type type) {
        try { return type.resolve().describe(); } catch (RuntimeException e) { return null; }
    }

    private String eraseGenerics(String type) {
        int generic = type.indexOf('<');
        return generic < 0 ? type : type.substring(0, generic);
    }

    private String jsonName(String fallback,
        com.github.javaparser.ast.NodeList<AnnotationExpr> annotations,
        JsonNamingStrategy namingStrategy) {
        return annotations.stream().filter(a -> a.getName().getIdentifier().equals("JsonProperty"))
            .map(this::attributes).map(values -> values.get("value"))
            .filter(String.class::isInstance).map(String.class::cast).findFirst()
            .orElseGet(() -> namingStrategy.apply(fallback));
    }

    private boolean isJsonIgnored(com.github.javaparser.ast.NodeList<AnnotationExpr> annotations) {
        return annotations.stream().anyMatch(annotation ->
            annotation.getName().getIdentifier().equals("JsonIgnore"));
    }

    private JsonNamingStrategy jsonNamingStrategy(TypeDeclaration<?> declaration) {
        return declaration.getAnnotations().stream()
            .filter(annotation -> annotation.getName().getIdentifier().equals("JsonNaming"))
            .map(this::attributes).map(values -> String.valueOf(values.get("value")))
            .map(JsonNamingStrategy::fromExpression).findFirst()
            .orElse(JsonNamingStrategy.IDENTITY);
    }

    private enum JsonNamingStrategy {
        IDENTITY, SNAKE_CASE, KEBAB_CASE, LOWER_DOT_CASE, UPPER_CAMEL_CASE, LOWER_CASE;

        static JsonNamingStrategy fromExpression(String expression) {
            if (expression.contains("SnakeCaseStrategy")) return SNAKE_CASE;
            if (expression.contains("KebabCaseStrategy")) return KEBAB_CASE;
            if (expression.contains("LowerDotCaseStrategy")) return LOWER_DOT_CASE;
            if (expression.contains("UpperCamelCaseStrategy")) return UPPER_CAMEL_CASE;
            if (expression.contains("LowerCaseStrategy")) return LOWER_CASE;
            return IDENTITY;
        }

        String apply(String javaName) {
            return switch (this) {
                case IDENTITY -> javaName;
                case SNAKE_CASE -> separated(javaName, '_');
                case KEBAB_CASE -> separated(javaName, '-');
                case LOWER_DOT_CASE -> separated(javaName, '.');
                case UPPER_CAMEL_CASE -> Character.toUpperCase(javaName.charAt(0))
                    + javaName.substring(1);
                case LOWER_CASE -> javaName.toLowerCase(java.util.Locale.ROOT);
            };
        }

        private String separated(String value, char separator) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (Character.isUpperCase(current) && index > 0) result.append(separator);
                result.append(Character.toLowerCase(current));
            }
            return result.toString();
        }
    }

    private Map<String, Object> attributes(AnnotationExpr annotation) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (annotation.isNormalAnnotationExpr()) {
            annotation.asNormalAnnotationExpr().getPairs().forEach(pair ->
                values.put(pair.getNameAsString(), annotationValue(pair.getValue())));
        } else if (annotation.isSingleMemberAnnotationExpr()) {
            values.put("value", annotationValue(
                annotation.asSingleMemberAnnotationExpr().getMemberValue()));
        }
        return values;
    }

    private Object annotationValue(Expression value) {
        if (value instanceof StringLiteralExpr literal) return literal.asString();
        if (value instanceof BooleanLiteralExpr literal) return literal.getValue();
        if (value instanceof IntegerLiteralExpr literal) return literal.asInt();
        if (value instanceof LongLiteralExpr literal) return literal.asLong();
        if (value instanceof DoubleLiteralExpr literal) return literal.asDouble();
        return value.toString();
    }

    private void relate(FactNode source, FactNode target, FactEdgeType type, int ordinal,
        String role, FactGraphAccumulator acc) {
        acc.addRelation(source, target, new FactEdge(
            ids.edgeId(source.id(), target.id(), type.name(), ordinal, role), source.id(),
            target.id(), type, ordinal, role));
    }
}
