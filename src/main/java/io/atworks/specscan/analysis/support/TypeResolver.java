package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
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

    public TypeResolver(List<Path> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    public Optional<ClassOrInterfaceDeclaration> resolveClassDeclaration(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }

        String cleanClassName = getCleanClassName(className);
        for (Path root : sourceRoots) {
            Path file = findClassFile(root, cleanClassName);
            if (file != null && Files.exists(file)) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    return cu.getClassByName(cleanClassName)
                        .or(() -> cu.getInterfaceByName(cleanClassName));
                } catch (IOException e) {
                    System.err.println("Warning: Failed to parse class file " + file + ": " + e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    public Map<String, Object> resolveExpandedSchema(
        String typeName,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> conditions
    ) {
        Map<String, FieldConstraints> constraintIndex = buildConstraintIndex(drafts, conditions);
        return resolveExpandedSchema(typeName, constraintIndex, new LinkedHashSet<>());
    }

    private String getCleanClassName(String className) {
        int angleIdx = className.indexOf('<');
        if (angleIdx != -1) {
            className = className.substring(0, angleIdx);
        }
        int dotIdx = className.lastIndexOf('.');
        if (dotIdx != -1) {
            className = className.substring(dotIdx + 1);
        }
        return className.trim();
    }

    private Path findClassFile(Path sourceRoot, String simpleClassName) {
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(simpleClassName + ".java"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, Object> resolveExpandedSchema(
        String typeName,
        Map<String, FieldConstraints> constraintIndex,
        Set<String> visitedTypes
    ) {
        String normalized = typeName == null ? "" : typeName.trim();
        if (normalized.isEmpty()) {
            return new LinkedHashMap<>(Map.of("type", "object"));
        }
        if (isWrapperType(normalized)) {
            return resolveExpandedSchema(extractFirstGenericArgument(normalized), constraintIndex, visitedTypes);
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

        Optional<String> primitiveType = resolvePrimitiveType(normalized);
        if (primitiveType.isPresent()) {
            return new LinkedHashMap<>(Map.of("type", primitiveType.get()));
        }

        String cleanClassName = getCleanClassName(normalized);
        if (!visitedTypes.add(cleanClassName)) {
            return new LinkedHashMap<>(Map.of("type", "object"));
        }

        Optional<ClassOrInterfaceDeclaration> classDeclaration = resolveClassDeclaration(cleanClassName);
        if (classDeclaration.isEmpty()) {
            visitedTypes.remove(cleanClassName);
            return new LinkedHashMap<>(Map.of("type", "object"));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (FieldDeclaration fieldDeclaration : classDeclaration.get().getFields()) {
            for (VariableDeclarator variable : fieldDeclaration.getVariables()) {
                String fieldName = variable.getNameAsString();
                Map<String, Object> fieldSchema = resolveExpandedSchema(
                    variable.getType().asString(),
                    constraintIndex,
                    visitedTypes
                );
                applyConstraints(fieldSchema, constraintIndex.get(fieldName));
                properties.put(fieldName, fieldSchema);
                if (isRequired(constraintIndex.get(fieldName))) {
                    required.add(fieldName);
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        visitedTypes.remove(cleanClassName);
        return schema;
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

    private Optional<String> resolvePrimitiveType(String typeName) {
        return switch (typeName) {
            case "String", "char", "Character" -> Optional.of("string");
            case "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte" -> Optional.of("integer");
            case "double", "Double", "float", "Float", "BigDecimal" -> Optional.of("number");
            case "boolean", "Boolean" -> Optional.of("boolean");
            default -> Optional.empty();
        };
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
