package io.atworks.specscan.analysis.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
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
            List<Map<String, Object>> endpointConditions = buildEndpointConditions(endpoint, drafts, conditions);
            operations.add(buildOperation(endpoint, endpointConditions, typeResolver));
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
        List<Map<String, Object>> endpointConditions,
        TypeResolver typeResolver
    ) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", endpoint.controllerMethod());
        operation.put("method", endpoint.httpMethod());
        operation.put("path", endpoint.path());
        operation.put("controllerClass", endpoint.controllerClass());
        operation.put("request", buildRequest(endpoint, endpointConditions, typeResolver));
        operation.put("response200", buildResponse(endpoint, typeResolver));
        operation.put("validationConditions", endpointConditions);
        return operation;
    }

    private Map<String, Object> buildRequest(
        ApiEndpoint endpoint,
        List<Map<String, Object>> endpointConditions,
        TypeResolver typeResolver
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contentType", hasBody(endpoint) ? "application/json" : null);
        request.put("pathParams", buildParameterGroup(endpoint, BindingLocation.PATH));
        request.put("queryParams", buildParameterGroup(endpoint, BindingLocation.QUERY));
        request.put("headers", buildParameterGroup(endpoint, BindingLocation.HEADER));

        RequestBinding bodyBinding = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() == BindingLocation.BODY)
            .findFirst()
            .orElse(null);
        if (bodyBinding == null) {
            request.put("bodySchema", null);
            request.put("bodyExample", null);
            return request;
        }

        List<ApiConditionDraft> bodyDrafts = new ArrayList<>();
        List<ApiCondition> bodyConditions = new ArrayList<>();
        for (Map<String, Object> endpointCondition : endpointConditions) {
            String targetPath = String.valueOf(endpointCondition.get("targetPath"));
            if (!targetPath.startsWith("$.")) {
                continue;
            }
            String operator = String.valueOf(endpointCondition.get("operator"));
            String expected = endpointCondition.get("expected") == null ? null : String.valueOf(endpointCondition.get("expected"));
            String source = String.valueOf(endpointCondition.get("source"));
            if ("BEAN_VALIDATION_ANNOTATION".equals(source)) {
                bodyDrafts.add(new ApiConditionDraft(targetPath, operator, expected, null, bodyBinding.sourceTrace()));
            } else {
                bodyConditions.add(new ApiCondition(targetPath, operator, expected, null, 0.0, null, bodyBinding.sourceTrace()));
            }
        }

        Map<String, Object> bodySchema = typeResolver.resolveExpandedSchema(bodyBinding.type(), bodyDrafts, bodyConditions);
        request.put("bodySchema", bodySchema);
        request.put("bodyExample", buildExample(bodySchema));
        return request;
    }

    private Map<String, Object> buildResponse(ApiEndpoint endpoint, TypeResolver typeResolver) {
        Map<String, Object> response = new LinkedHashMap<>();
        String responseType = endpoint.responseBinding().type();
        if (responseType == null || "void".equalsIgnoreCase(responseType)) {
            response.put("contentType", null);
            response.put("schema", null);
            response.put("example", null);
            return response;
        }

        Map<String, Object> schema = typeResolver.resolveExpandedSchema(responseType, List.of(), List.of());
        response.put("contentType", "application/json");
        response.put("schema", schema);
        response.put("example", buildExample(schema));
        return response;
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
        Set<String> parameterNames = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() != BindingLocation.BODY)
            .map(RequestBinding::parameterName)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        boolean hasBody = hasBody(endpoint);

        for (ApiConditionDraft draft : drafts) {
            if (hasBody || parameterNames.contains(draft.targetPath())) {
                endpointConditions.add(buildConditionMap(
                    resolveTargetLocation(endpoint, draft.targetPath()),
                    toJsonPath(draft.targetPath()),
                    draft.operator(),
                    draft.expected(),
                    "BEAN_VALIDATION_ANNOTATION"
                ));
            }
        }
        for (ApiCondition condition : conditions) {
            if (hasBody || parameterNames.contains(condition.targetPath())) {
                endpointConditions.add(buildConditionMap(
                    resolveTargetLocation(endpoint, condition.targetPath()),
                    toJsonPath(condition.targetPath()),
                    condition.operator(),
                    condition.expected(),
                    "SERVICE_LOGIC_HINT"
                ));
            }
        }
        return endpointConditions;
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

    private String resolveTargetLocation(ApiEndpoint endpoint, String targetPath) {
        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.parameterName().equals(targetPath)) {
                return binding.targetLocation().name();
            }
        }
        return hasBody(endpoint) ? "BODY" : "QUERY";
    }

    private String toJsonPath(String targetPath) {
        return targetPath != null && targetPath.startsWith("$.") ? targetPath : "$." + targetPath;
    }

    private boolean hasBody(ApiEndpoint endpoint) {
        return endpoint.requestBindings().stream().anyMatch(binding -> binding.targetLocation() == BindingLocation.BODY);
    }

    private Map<String, Object> primitiveSchema(String typeName) {
        if ("int".equals(typeName) || "Integer".equals(typeName) || "long".equals(typeName) || "Long".equals(typeName)) {
            return new LinkedHashMap<>(Map.of("type", "integer"));
        }
        if ("boolean".equals(typeName) || "Boolean".equals(typeName)) {
            return new LinkedHashMap<>(Map.of("type", "boolean"));
        }
        return new LinkedHashMap<>(Map.of("type", "string"));
    }

    @SuppressWarnings("unchecked")
    private Object buildExample(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        String type = String.valueOf(schema.get("type"));
        return switch (type) {
            case "string" -> "";
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
}
