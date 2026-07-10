package io.atworks.specscan.analysis.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.ConditionLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.SourceRootCandidate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExecutionSpecExporter {

    private final ObjectMapper objectMapper;

    public ExecutionSpecExporter() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String export(
        StaticScanResult scanResult,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> conditions,
        RepositorySource repositorySource
    ) {
        TypeResolver typeResolver = new TypeResolver(resolveSourceRoots(repositorySource));
        List<Map<String, Object>> operations = new ArrayList<>();
        for (ApiEndpoint endpoint : scanResult.endpoints()) {
            RequestSpec requestSpec = buildRequest(endpoint, drafts, conditions, typeResolver);
            operations.add(buildOperation(endpoint, requestSpec, typeResolver));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operations", operations);
        payload.put("warningCount", scanResult.warnings().size());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize execution spec export.", e);
        }
    }

    private List<Path> resolveSourceRoots(RepositorySource repositorySource) {
        Path workspaceRoot = Path.of(repositorySource.workspaceContext().workspacePath());
        List<Path> sourceRoots = new ArrayList<>();
        for (SourceRootCandidate sourceRoot : repositorySource.sourceRoots()) {
            sourceRoots.add(workspaceRoot.resolve(sourceRoot.rootPath()));
        }
        return sourceRoots;
    }

    private Map<String, Object> buildOperation(
        ApiEndpoint endpoint,
        RequestSpec requestSpec,
        TypeResolver typeResolver
    ) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", endpoint.controllerMethod());
        operation.put("method", endpoint.httpMethod());
        operation.put("path", endpoint.path());
        operation.put("controllerClass", endpoint.controllerClass());
        operation.put("request", requestSpec.request());
        operation.put("response200", buildResponse(endpoint, typeResolver));
        operation.put("validationConditions", requestSpec.endpointConditions());
        return operation;
    }

    private RequestSpec buildRequest(
        ApiEndpoint endpoint,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> conditions,
        TypeResolver typeResolver
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contentType", hasBody(endpoint) ? "application/json" : null);
        request.put("pathParams", buildParameterGroup(endpoint, BindingLocation.PATH));
        request.put("queryParams", buildParameterGroup(endpoint, BindingLocation.QUERY));
        request.put("headers", buildParameterGroup(endpoint, BindingLocation.HEADER));
        List<Map<String, Object>> endpointConditions = buildEndpointConditions(endpoint, drafts, conditions);

        RequestBinding bodyBinding = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() == BindingLocation.BODY)
            .findFirst()
            .orElse(null);
        if (bodyBinding == null) {
            request.put("bodySchema", null);
            request.put("bodyExample", null);
            return new RequestSpec(request, endpointConditions, Set.of());
        }

        List<ApiConditionDraft> bodyDrafts = new ArrayList<>();
        List<ApiCondition> bodyConditions = new ArrayList<>();
        for (Map<String, Object> endpointCondition : endpointConditions) {
            if (!"BODY".equals(endpointCondition.get("targetLocation"))) {
                continue;
            }
            String targetPath = String.valueOf(endpointCondition.get("targetPath"));
            String operator = String.valueOf(endpointCondition.get("operator"));
            String expected = endpointCondition.get("expected") == null ? null : String.valueOf(endpointCondition.get("expected"));
            String source = String.valueOf(endpointCondition.get("source"));
            if ("BEAN_VALIDATION_ANNOTATION".equals(source)) {
                bodyDrafts.add(new ApiConditionDraft(targetPath, operator, expected, null, bodyBinding.sourceTrace()));
            } else {
                bodyConditions.add(new ApiCondition(ConditionLocation.BODY, targetPath, operator, expected, null, 0.0, null, bodyBinding.sourceTrace()));
            }
        }

        Map<String, Object> bodySchema = typeResolver.resolveExpandedSchema(bodyBinding.type(), bodyDrafts, bodyConditions);
        request.put("bodySchema", bodySchema);
        request.put("bodyExample", buildExample(bodySchema));
        return new RequestSpec(request, endpointConditions, Set.of());
    }

    private Map<String, Object> buildResponse(ApiEndpoint endpoint, TypeResolver typeResolver) {
        Map<String, Object> response = new LinkedHashMap<>();
        String responseType = endpoint.responseBinding().type();
        if (responseType == null
            || "void".equalsIgnoreCase(responseType)
            || EndpointExtractor.MVC_VIEW_RESPONSE.equals(responseType)) {
            response.put("contentType", null);
            response.put("schema", null);
            response.put("example", null);
            return response;
        }

        Map<String, Object> schema = isPrimitiveResponseType(responseType)
            ? primitiveSchema(responseType)
            : typeResolver.resolveExpandedSchema(responseType, List.of(), List.of());
        response.put("contentType", resolveResponseContentType(responseType));
        response.put("schema", schema);
        response.put("example", buildExample(schema));
        return response;
    }

    private boolean isPrimitiveResponseType(String responseType) {
        return switch (responseType) {
            case "String", "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte",
                "double", "Double", "float", "Float", "BigDecimal",
                "boolean", "Boolean",
                "LocalDate", "java.time.LocalDate",
                "LocalDateTime", "java.time.LocalDateTime", "Instant", "java.time.Instant",
                "Date", "java.util.Date",
                "UUID", "java.util.UUID" -> true;
            default -> false;
        };
    }

    private String resolveResponseContentType(String responseType) {
        if ("String".equals(responseType)) {
            return "text/plain";
        }
        return "application/json";
    }

    private List<Map<String, Object>> buildParameterGroup(ApiEndpoint endpoint, BindingLocation location) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.targetLocation() != location) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", binding.parameterName());
            item.put("required", binding.isRequired());
            item.put("schema", primitiveSchema(binding.type()));
            if (binding.description() != null && !binding.description().isBlank()) {
                item.put("description", binding.description());
            }
            if (binding.example() != null && !binding.example().isBlank()) {
                item.put("example", binding.example());
            }
            if (binding.defaultValue() != null && !binding.defaultValue().isBlank()) {
                item.put("defaultValue", binding.defaultValue());
            }
            if (!binding.enumValues().isEmpty()) {
                item.put("enumValues", binding.enumValues());
            }
            parameters.add(item);
        }
        return parameters;
    }

    private List<Map<String, Object>> buildEndpointConditions(
        ApiEndpoint endpoint,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> conditions
    ) {
        List<Map<String, Object>> endpointConditions = new ArrayList<>();
        Set<String> conditionKeys = new LinkedHashSet<>();

        for (ApiConditionDraft draft : drafts) {
            addEndpointCondition(endpointConditions, conditionKeys, endpoint, draft.targetPath(), draft.operator(), draft.expected(),
                "BEAN_VALIDATION_ANNOTATION", null);
        }
        for (ApiCondition condition : conditions) {
            addEndpointCondition(endpointConditions, conditionKeys, endpoint, condition.targetPath(), condition.operator(), condition.expected(),
                "SERVICE_LOGIC_HINT", condition.targetLocation());
        }
        return endpointConditions;
    }

    private void addEndpointCondition(
        List<Map<String, Object>> endpointConditions,
        Set<String> conditionKeys,
        ApiEndpoint endpoint,
        String rawTargetPath,
        String operator,
        String expected,
        String source,
        ConditionLocation explicitLocation
    ) {
        ResolvedCondition resolved = resolveCondition(endpoint, rawTargetPath, explicitLocation);
        if (resolved == null) {
            return;
        }

        String dedupeKey = String.join("|",
            resolved.targetLocation(),
            resolved.targetPath(),
            String.valueOf(operator),
            String.valueOf(expected),
            source
        );
        if (!conditionKeys.add(dedupeKey)) {
            return;
        }

        endpointConditions.add(buildConditionMap(
            resolved.targetLocation(),
            resolved.targetPath(),
            operator,
            expected,
            source
        ));
    }

    private ResolvedCondition resolveCondition(ApiEndpoint endpoint, String rawTargetPath, ConditionLocation explicitLocation) {
        if (explicitLocation != null && explicitLocation != ConditionLocation.UNKNOWN) {
            return new ResolvedCondition(explicitLocation.name(), normalizeTargetPathForLocation(rawTargetPath));
        }

        String normalizedName = normalizeConditionName(rawTargetPath);
        if (normalizedName.isBlank()) {
            return null;
        }

        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.targetLocation() == BindingLocation.BODY) {
                continue;
            }
            if (binding.parameterName().equals(rawTargetPath) || binding.parameterName().equals(normalizedName)) {
                return new ResolvedCondition(binding.targetLocation().name(), "$." + binding.parameterName());
            }
        }

        if (hasBody(endpoint)) {
            return new ResolvedCondition("BODY", toBodyJsonPath(rawTargetPath, normalizedName));
        }
        return null;
    }

    private String normalizeConditionName(String targetPath) {
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

    private String toBodyJsonPath(String rawTargetPath, String normalizedName) {
        if (rawTargetPath != null && rawTargetPath.startsWith("$.") && !rawTargetPath.isBlank()) {
            return rawTargetPath;
        }
        return "$." + normalizedName;
    }

    private String normalizeTargetPathForLocation(String rawTargetPath) {
        if (rawTargetPath == null || rawTargetPath.isBlank()) {
            return "$";
        }
        if (rawTargetPath.startsWith("$.")) {
            return rawTargetPath;
        }
        return "$." + normalizeConditionName(rawTargetPath);
    }

    private Map<String, Object> buildConditionMap(
        String targetLocation,
        String targetPath,
        String operator,
        String expected,
        String source
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("targetLocation", targetLocation);
        item.put("targetPath", targetPath);
        item.put("operator", operator);
        item.put("expected", expected);
        item.put("source", source);
        return item;
    }

    private boolean hasBody(ApiEndpoint endpoint) {
        return endpoint.requestBindings().stream().anyMatch(binding -> binding.targetLocation() == BindingLocation.BODY);
    }

    private Map<String, Object> primitiveSchema(String typeName) {
        return switch (typeName) {
            case "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte" ->
                new LinkedHashMap<>(Map.of("type", "integer"));
            case "double", "Double", "float", "Float", "BigDecimal" ->
                new LinkedHashMap<>(Map.of("type", "number"));
            case "boolean", "Boolean" ->
                new LinkedHashMap<>(Map.of("type", "boolean"));
            case "LocalDate", "java.time.LocalDate" ->
                new LinkedHashMap<>(Map.of("type", "string", "format", "date"));
            case "LocalDateTime", "java.time.LocalDateTime", "Instant", "java.time.Instant", "Date", "java.util.Date" ->
                new LinkedHashMap<>(Map.of("type", "string", "format", "date-time"));
            case "UUID", "java.util.UUID" ->
                new LinkedHashMap<>(Map.of("type", "string", "format", "uuid"));
            default ->
                new LinkedHashMap<>(Map.of("type", "string"));
        };
    }

    @SuppressWarnings("unchecked")
    private Object buildExample(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        String type = String.valueOf(schema.get("type"));
        return switch (type) {
            case "string" -> buildStringExample(schema);
            case "integer" -> 0;
            case "number" -> 0;
            case "boolean" -> false;
            case "array" -> List.of(buildExample((Map<String, Object>) schema.get("items")));
            case "object" -> {
                Map<String, Object> example = new LinkedHashMap<>();
                Object propertiesValue = schema.get("properties");
                if (propertiesValue instanceof Map<?, ?> properties) {
                    for (Map.Entry<?, ?> entry : properties.entrySet()) {
                        if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                            example.put(String.valueOf(entry.getKey()), buildExample((Map<String, Object>) propertySchema));
                        }
                    }
                }
                yield example;
            }
            default -> null;
        };
    }

    private Object buildStringExample(Map<String, Object> schema) {
        Object enumValues = schema.get("enumValues");
        if (enumValues instanceof List<?> values && !values.isEmpty()) {
            return String.valueOf(values.get(0));
        }

        String format = schema.get("format") == null ? null : String.valueOf(schema.get("format"));
        if ("date".equals(format)) {
            return "2024-01-01";
        }
        if ("date-time".equals(format)) {
            return "2024-01-01T00:00:00Z";
        }
        if ("uuid".equals(format)) {
            return "00000000-0000-0000-0000-000000000000";
        }
        return "";
    }

    private record RequestSpec(
        Map<String, Object> request,
        List<Map<String, Object>> endpointConditions,
        Set<String> bodyFieldNames
    ) {}

    private record ResolvedCondition(String targetLocation, String targetPath) {}
}
