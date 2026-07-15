package io.atworks.specscan.analysis.support;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class TypeResolver {

    private final List<Path> sourceRoots;
    private final CombinedTypeSolver typeSolver;

    public TypeResolver(List<Path> sourceRoots) {
        this.sourceRoots = sourceRoots;
        this.typeSolver = createTypeSolver(sourceRoots);
        StaticJavaParser.getConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
            .setSymbolResolver(new JavaSymbolSolver(typeSolver));
    }

    public Optional<ClassOrInterfaceDeclaration> resolveClassDeclaration(String className) {
        return resolveTypeDeclaration(className)
            .filter(ClassOrInterfaceDeclaration.class::isInstance)
            .map(ClassOrInterfaceDeclaration.class::cast);
    }

    public Map<String, Object> resolveExpandedSchema(
        String typeName,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> conditions
    ) {
        Map<String, FieldConstraints> constraintIndex = buildConstraintIndex(drafts, conditions);
        return resolveExpandedSchema(typeName, constraintIndex, new LinkedHashSet<>());
    }

    public Optional<MethodDeclaration> resolveMethodDeclaration(ResolvedMethodDeclaration resolvedMethod) {
        return resolveClassDeclaration(resolvedMethod.declaringType())
            .flatMap(ownerDecl -> ownerDecl.getMethodsByName(resolvedMethod.getName()).stream()
                .filter(candidate -> matchesResolvedMethod(candidate, resolvedMethod))
                .findFirst());
    }

    public Optional<ClassOrInterfaceDeclaration> resolveClassDeclaration(ResolvedReferenceTypeDeclaration resolvedType) {
        return resolveTypeDeclaration(resolvedType.getQualifiedName())
            .filter(ClassOrInterfaceDeclaration.class::isInstance)
            .map(ClassOrInterfaceDeclaration.class::cast);
    }

    public Optional<ResolvedMethodDeclaration> resolveMethodCall(MethodCallExpr call) {
        try {
            return Optional.of(call.resolve());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public Optional<MethodDeclaration> resolveStaticSourceMethodCall(MethodCallExpr call) {
        if (call.getScope().isEmpty()) return Optional.empty();
        String scope = call.getScope().get().toString();
        String simple = scope.substring(scope.lastIndexOf('.') + 1);
        if (simple.isBlank() || !Character.isUpperCase(simple.charAt(0))) return Optional.empty();
        List<MethodDeclaration> matches = resolveClassDeclaration(scope).stream()
            .flatMap(type -> type.getMethodsByName(call.getNameAsString()).stream())
            .filter(method -> method.isStatic() && method.getParameters().size() == call.getArguments().size())
            .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    public String qualifiedOwner(MethodDeclaration method) {
        String packageName = method.findCompilationUnit().flatMap(CompilationUnit::getPackageDeclaration)
            .map(value -> value.getNameAsString()).orElse("");
        List<String> types = new ArrayList<>();
        com.github.javaparser.ast.Node current = method.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof TypeDeclaration<?> type) types.add(0, type.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        return (packageName.isBlank() ? "" : packageName + ".") + String.join(".", types);
    }

    public String qualifiedOwner(TypeDeclaration<?> type) {
        String packageName = type.findCompilationUnit().flatMap(CompilationUnit::getPackageDeclaration)
            .map(value -> value.getNameAsString()).orElse("");
        List<String> types = new ArrayList<>();
        com.github.javaparser.ast.Node current = type;
        while (current != null) {
            if (current instanceof TypeDeclaration<?> declaration) types.add(0, declaration.getNameAsString());
            current = current.getParentNode().orElse(null);
        }
        return (packageName.isBlank() ? "" : packageName + ".") + String.join(".", types);
    }

    public Optional<ResolvedMethodDeclaration> resolveMethodReference(MethodReferenceExpr reference) {
        try { return Optional.of(reference.resolve()); }
        catch (RuntimeException e) { return Optional.empty(); }
    }

    public Optional<ResolvedConstructorDeclaration> resolveObjectCreation(ObjectCreationExpr creation) {
        try { return Optional.of(creation.resolve()); }
        catch (RuntimeException e) { return Optional.empty(); }
    }

    public Optional<ResolvedConstructorDeclaration> resolveConstructorInvocation(
        ExplicitConstructorInvocationStmt invocation) {
        try { return Optional.of(invocation.resolve()); }
        catch (RuntimeException e) { return Optional.empty(); }
    }

    public Optional<ConstructorDeclaration> resolveConstructorDeclaration(
        ResolvedConstructorDeclaration resolvedConstructor) {
        return resolveClassDeclaration(resolvedConstructor.declaringType())
            .flatMap(owner -> owner.getConstructors().stream()
                .filter(candidate -> candidate.getParameters().size() == resolvedConstructor.getNumberOfParams())
                .findFirst());
    }

    private Optional<TypeDeclaration<?>> resolveTypeDeclaration(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return Optional.empty();
        }

        Optional<TypeDeclaration<?>> byQualifiedName = resolveQualifiedTypeDeclaration(typeName);
        if (byQualifiedName.isPresent()) {
            return byQualifiedName;
        }

        List<String> segments = splitTypeSegments(typeName);
        if (segments.isEmpty()) {
            return Optional.empty();
        }

        List<String> fileCandidates = new ArrayList<>();
        fileCandidates.add(segments.get(0));
        String simpleName = segments.get(segments.size() - 1);
        if (!fileCandidates.contains(simpleName)) {
            fileCandidates.add(simpleName);
        }

        for (Path root : sourceRoots) {
            for (String candidate : fileCandidates) {
                Path file = findClassFile(root, candidate);
                if (file == null || !Files.exists(file)) {
                    continue;
                }
                Optional<TypeDeclaration<?>> resolved = resolveTypeDeclaration(file, segments);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
            Optional<TypeDeclaration<?>> fallback = findTypeBySimpleName(root, simpleName);
            if (fallback.isPresent()) {
                return fallback;
            }
        }
        return Optional.empty();
    }

    private Optional<TypeDeclaration<?>> resolveQualifiedTypeDeclaration(String typeName) {
        String normalized = stripGenericAndQualifier(normalizeTypeName(typeName));
        if (normalized.isBlank() || !normalized.contains(".")) {
            return Optional.empty();
        }

        String[] tokens = normalized.split("\\.");
        List<String> packageSegments = new ArrayList<>();
        List<String> typeSegments = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (typeSegments.isEmpty() && !Character.isUpperCase(token.charAt(0))) {
                packageSegments.add(token);
            } else {
                typeSegments.add(token);
            }
        }
        if (typeSegments.isEmpty()) {
            return Optional.empty();
        }

        for (Path root : sourceRoots) {
            Path candidate = root;
            for (String pkg : packageSegments) {
                candidate = candidate.resolve(pkg);
            }
            candidate = candidate.resolve(typeSegments.get(0) + ".java");
            if (!Files.exists(candidate)) {
                continue;
            }
            Optional<TypeDeclaration<?>> resolved = resolveTypeDeclaration(candidate, typeSegments);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private Optional<TypeDeclaration<?>> resolveTypeDeclaration(Path file, List<String> segments) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            for (TypeDeclaration<?> type : cu.getTypes()) {
                Optional<TypeDeclaration<?>> resolved = resolveNestedType(type, segments, 0);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Warning: Failed to parse class file " + file + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<TypeDeclaration<?>> resolveNestedType(
        TypeDeclaration<?> declaration,
        List<String> segments,
        int index
    ) {
        if (index >= segments.size() || !declaration.getNameAsString().equals(segments.get(index))) {
            return Optional.empty();
        }
        if (index == segments.size() - 1) {
            return Optional.of(declaration);
        }

        for (BodyDeclaration<?> member : declaration.getMembers()) {
            if (member instanceof TypeDeclaration<?> nestedType) {
                Optional<TypeDeclaration<?>> resolved = resolveNestedType(nestedType, segments, index + 1);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }
        return Optional.empty();
    }

    private List<String> splitTypeSegments(String typeName) {
        String normalized = stripGenericAndQualifier(normalizeTypeName(typeName));
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> segments = new ArrayList<>();
        for (String token : normalized.split("[.$]")) {
            if (!token.isBlank() && Character.isUpperCase(token.charAt(0))) {
                segments.add(token.trim());
            }
        }
        if (segments.isEmpty()) {
            segments.add(normalized.trim());
        }
        return segments;
    }

    private String stripGenericAndQualifier(String typeName) {
        int angleIdx = typeName.indexOf('<');
        if (angleIdx != -1) {
            typeName = typeName.substring(0, angleIdx);
        }
        return typeName.trim();
    }

    private String normalizeTypeName(String typeName) {
        String normalized = typeName == null ? "" : typeName.trim();
        if (normalized.startsWith("? extends ")) {
            return normalized.substring("? extends ".length()).trim();
        }
        if (normalized.startsWith("? super ")) {
            return normalized.substring("? super ".length()).trim();
        }
        return normalized;
    }

    private Path findClassFile(Path sourceRoot, String simpleClassName) {
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(simpleClassName + ".java"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Optional<TypeDeclaration<?>> findTypeBySimpleName(Path sourceRoot, String simpleName) {
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .map(path -> resolveTypeBySimpleName(path, simpleName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<TypeDeclaration<?>> resolveTypeBySimpleName(Path file, String simpleName) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(TypeDeclaration.class).stream()
                .filter(type -> type.getNameAsString().equals(simpleName))
                .findFirst()
                .map(type -> (TypeDeclaration<?>) type);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> resolveExpandedSchema(
        String typeName,
        Map<String, FieldConstraints> constraintIndex,
        Set<String> visitedTypes
    ) {
        String normalized = normalizeTypeName(typeName);
        if (normalized.isEmpty()) {
            return new LinkedHashMap<>(Map.of("type", "object"));
        }
        if (isWrapperType(normalized)) {
            return resolveExpandedSchema(extractFirstGenericArgument(normalized), constraintIndex, visitedTypes);
        }
        if (isReactiveCollectionType(normalized)) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", resolveExpandedSchema(extractFirstGenericArgument(normalized), constraintIndex, visitedTypes));
            return schema;
        }
        if (isCollectionType(normalized)) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", resolveExpandedSchema(extractFirstGenericArgument(normalized), constraintIndex, visitedTypes));
            return schema;
        }
        if (isMapType(normalized)) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("additionalProperties", true);
            return schema;
        }

        Optional<Map<String, Object>> scalarSchema = resolveScalarSchema(normalized);
        if (scalarSchema.isPresent()) {
            return scalarSchema.get();
        }

        String visitedKey = stripGenericAndQualifier(normalized);
        if (!visitedTypes.add(visitedKey)) {
            return new LinkedHashMap<>(Map.of("type", "object"));
        }

        Optional<TypeDeclaration<?>> typeDeclaration = resolveTypeDeclaration(normalized);
        if (typeDeclaration.isEmpty()) {
            visitedTypes.remove(visitedKey);
            return new LinkedHashMap<>(Map.of("type", "object"));
        }

        Map<String, Object> schema = buildObjectSchema(typeDeclaration.get(), constraintIndex, visitedTypes);
        visitedTypes.remove(visitedKey);
        return schema;
    }

    private Map<String, Object> buildObjectSchema(
        TypeDeclaration<?> declaration,
        Map<String, FieldConstraints> constraintIndex,
        Set<String> visitedTypes
    ) {
        if (declaration instanceof EnumDeclaration enumDeclaration) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "string");
            schema.put("enumValues", enumDeclaration.getEntries().stream().map(entry -> entry.getNameAsString()).toList());
            return schema;
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (declaration instanceof RecordDeclaration recordDeclaration) {
            for (Parameter component : recordDeclaration.getParameters()) {
                addProperty(properties, required, component.getNameAsString(), component.getType().asString(), constraintIndex, visitedTypes);
            }
        } else if (declaration instanceof ClassOrInterfaceDeclaration classDeclaration) {
            for (FieldDeclaration fieldDeclaration : classDeclaration.getFields()) {
                if (fieldDeclaration.isStatic()) {
                    continue;
                }
                for (VariableDeclarator variable : fieldDeclaration.getVariables()) {
                    addProperty(properties, required, variable.getNameAsString(), variable.getType().asString(), constraintIndex, visitedTypes);
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private void addProperty(
        Map<String, Object> properties,
        List<String> required,
        String fieldName,
        String fieldType,
        Map<String, FieldConstraints> constraintIndex,
        Set<String> visitedTypes
    ) {
        Map<String, Object> fieldSchema = resolveExpandedSchema(fieldType, constraintIndex, visitedTypes);
        applyConstraints(fieldSchema, constraintIndex.get(fieldName));
        properties.put(fieldName, fieldSchema);
        if (isRequired(constraintIndex.get(fieldName))) {
            required.add(fieldName);
        }
    }

    private Map<String, FieldConstraints> buildConstraintIndex(
        List<ApiConditionDraft> drafts,
        List<ApiCondition> conditions
    ) {
        Map<String, FieldConstraints> index = new LinkedHashMap<>();
        for (ApiConditionDraft draft : drafts) {
            String fieldName = normalizeFieldName(draft.targetPath());
            if (!fieldName.isEmpty()) {
                applyCondition(index.computeIfAbsent(fieldName, _k -> new FieldConstraints()), draft.operator(), draft.expected());
            }
        }
        for (ApiCondition condition : conditions) {
            String fieldName = normalizeFieldName(condition.targetPath());
            if (!fieldName.isEmpty()) {
                applyCondition(index.computeIfAbsent(fieldName, _k -> new FieldConstraints()), condition.operator(), condition.expected());
            }
        }
        return index;
    }

    private void applyCondition(FieldConstraints constraints, String operator, String expected) {
        if (operator == null) {
            return;
        }
        switch (operator) {
            case "NOT_NULL", "NOT_BLANK", "NOT_EMPTY" -> constraints.required = true;
            case "SIZE" -> parseSizeConstraint(expected, constraints);
            case "PATTERN" -> constraints.pattern = expected;
            case "EMAIL" -> constraints.format = "email";
            case "MIN_AGE" -> constraints.minimum = parseInteger(expected);
            default -> {
            }
        }
    }

    private void parseSizeConstraint(String expected, FieldConstraints constraints) {
        if (expected == null || expected.isBlank()) {
            return;
        }
        String[] parts = expected.split(",");
        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length != 2) {
                continue;
            }
            Integer value = parseInteger(kv[1].trim());
            if (value == null) {
                continue;
            }
            if ("min".equals(kv[0].trim())) {
                constraints.minLength = value;
            } else if ("max".equals(kv[0].trim())) {
                constraints.maxLength = value;
            }
        }
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeFieldName(String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            return "";
        }
        String normalized = targetPath.startsWith("$.") ? targetPath.substring(2) : targetPath;
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex != -1) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized.trim();
    }

    private void applyConstraints(Map<String, Object> fieldSchema, FieldConstraints constraints) {
        if (constraints == null) {
            return;
        }
        if (constraints.minLength != null) {
            fieldSchema.put("minLength", constraints.minLength);
        }
        if (constraints.maxLength != null) {
            fieldSchema.put("maxLength", constraints.maxLength);
        }
        if (constraints.pattern != null && !constraints.pattern.isBlank()) {
            fieldSchema.put("pattern", constraints.pattern);
        }
        if (constraints.format != null && !constraints.format.isBlank()) {
            fieldSchema.put("format", constraints.format);
        }
        if (constraints.minimum != null) {
            fieldSchema.put("minimum", constraints.minimum);
        }
    }

    private boolean isRequired(FieldConstraints constraints) {
        return constraints != null && constraints.required;
    }

    private boolean isWrapperType(String typeName) {
        return typeName.startsWith("ResponseEntity<")
            || typeName.startsWith("HttpEntity<")
            || typeName.startsWith("Optional<")
            || typeName.startsWith("Mono<");
    }

    private boolean isReactiveCollectionType(String typeName) {
        return typeName.startsWith("Flux<");
    }

    private boolean isCollectionType(String typeName) {
        return typeName.startsWith("List<")
            || typeName.startsWith("Set<")
            || typeName.startsWith("Collection<")
            || typeName.startsWith("Iterable<");
    }

    private boolean isMapType(String typeName) {
        return typeName.startsWith("Map<");
    }

    private String extractFirstGenericArgument(String typeName) {
        int start = typeName.indexOf('<');
        int end = typeName.lastIndexOf('>');
        if (start == -1 || end == -1 || end <= start + 1) {
            return typeName;
        }
        String inner = typeName.substring(start + 1, end).trim();
        int nestedDepth = 0;
        for (int i = 0; i < inner.length(); i++) {
            char current = inner.charAt(i);
            if (current == '<') {
                nestedDepth++;
            } else if (current == '>') {
                nestedDepth--;
            } else if (current == ',' && nestedDepth == 0) {
                return inner.substring(0, i).trim();
            }
        }
        return inner;
    }

    private Optional<Map<String, Object>> resolveScalarSchema(String typeName) {
        return switch (typeName) {
            case "String", "char", "Character" -> Optional.of(schema("string"));
            case "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte" -> Optional.of(schema("integer"));
            case "double", "Double", "float", "Float", "BigDecimal" -> Optional.of(schema("number"));
            case "boolean", "Boolean" -> Optional.of(schema("boolean"));
            case "LocalDate", "java.time.LocalDate" -> Optional.of(schema("string", "format", "date"));
            case "LocalDateTime", "java.time.LocalDateTime",
                 "Instant", "java.time.Instant",
                 "Date", "java.util.Date",
                 "OffsetDateTime", "java.time.OffsetDateTime",
                 "ZonedDateTime", "java.time.ZonedDateTime" ->
                Optional.of(schema("string", "format", "date-time"));
            case "UUID", "java.util.UUID" -> Optional.of(schema("string", "format", "uuid"));
            case "Object" -> Optional.of(schema("object"));
            default -> Optional.empty();
        };
    }

    private CombinedTypeSolver createTypeSolver(List<Path> sourceRoots) {
        CombinedTypeSolver combined = new CombinedTypeSolver();
        combined.add(new ReflectionTypeSolver(false));
        for (Path sourceRoot : sourceRoots) {
            if (Files.exists(sourceRoot)) {
                combined.add(new JavaParserTypeSolver(sourceRoot));
            }
        }
        return combined;
    }

    private boolean matchesResolvedMethod(MethodDeclaration candidate, ResolvedMethodDeclaration resolvedMethod) {
        try {
            return candidate.resolve().getQualifiedSignature().equals(resolvedMethod.getQualifiedSignature());
        } catch (RuntimeException e) {
            return candidate.getNameAsString().equals(resolvedMethod.getName())
                && hasMatchingArity(candidate, resolvedMethod);
        }
    }

    private boolean hasMatchingArity(CallableDeclaration<?> candidate, ResolvedMethodDeclaration resolvedMethod) {
        return candidate.getParameters().size() == resolvedMethod.getNumberOfParams();
    }

    private Map<String, Object> schema(String type) {
        return new LinkedHashMap<>(Map.of("type", type));
    }

    private Map<String, Object> schema(String type, String key, String value) {
        Map<String, Object> schema = schema(type);
        schema.put(key, value);
        return schema;
    }

    private static final class FieldConstraints {
        private boolean required;
        private Integer minLength;
        private Integer maxLength;
        private String pattern;
        private String format;
        private Integer minimum;
    }
}
