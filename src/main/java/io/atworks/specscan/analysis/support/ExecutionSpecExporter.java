package io.atworks.specscan.analysis.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.output.CandidateOutputDiagnostic;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.output.ExcludedBusinessRule;
import io.atworks.specscan.analysis.domain.output.ExecutableCondition;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.SourceRootCandidate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        Map<String, EndpointRuleOutput> ruleOutputs,
        List<IngestionWarning> warnings,
        RepositorySource repositorySource
    ) {
        TypeResolver typeResolver = new TypeResolver(resolveSourceRoots(repositorySource));
        List<Map<String, Object>> operations = new ArrayList<>();
        for (ApiEndpoint endpoint : scanResult.endpoints()) {
            EndpointRuleOutput output = ruleOutputs.getOrDefault(OperationKey.of(endpoint).externalKey(),
                EndpointRuleOutput.empty(endpoint.path()));
            RequestSpec base = buildRequest(endpoint, typeResolver);
            RequestSpec projected = new RequestSpec(base.request(), mapExecutable(output.requestPreconditions()),
                mapExecutable(output.responseAssertions()), mapExcluded(output.excludedBusinessRules()), Set.of());
            Map<String, Object> operation = buildOperation(endpoint, projected, typeResolver);
            if (!output.diagnostics().isEmpty()) operation.put("conditionDiagnostics", mapDiagnostics(output.diagnostics()));
            operations.add(operation);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operations", operations);
        payload.put("warningCount", warnings.size());
        if (!warnings.isEmpty()) payload.put("warnings", buildWarnings(warnings));
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
        operation.put("requestPreconditions", requestSpec.requestPreconditions());
        operation.put("responseAssertions", requestSpec.responseAssertions());
        operation.put("excludedBusinessRules", requestSpec.excludedBusinessRules());
        return operation;
    }

    private RequestSpec buildRequest(ApiEndpoint endpoint, TypeResolver typeResolver) {
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
            return new RequestSpec(request, List.of(), List.of(), List.of(), Set.of());
        }

        Map<String, Object> bodySchema = typeResolver.resolveExpandedSchema(bodyBinding.type(), List.of(), List.of());
        request.put("bodySchema", bodySchema);
        request.put("bodyExample", buildExample(bodySchema));
        return new RequestSpec(request, List.of(), List.of(), List.of(), Set.of());
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



    private List<Map<String, Object>> mapExecutable(List<ExecutableCondition> conditions) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExecutableCondition condition : conditions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetLocation", condition.targetLocation());
            item.put("targetPath", condition.targetPath());
            item.put("operator", condition.operator());
            Object expected = condition.expectedValues().size() == 1
                ? condition.expectedValues().get(0) : condition.expectedValues();
            item.put("expected", expected);
            item.put("expectedSource", condition.expectedSource());
            item.put("ruleId", condition.ruleId());
            item.put("confidence", condition.confidence());
            item.put("evidence", condition.evidence());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mapExcluded(List<ExcludedBusinessRule> rules) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExcludedBusinessRule rule : rules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleId", rule.ruleId()); item.put("category", rule.category());
            item.put("constraintKind", rule.constraintKind()); item.put("reasonCode", rule.reasonCode());
            item.put("extractionStatus", rule.extractionStatus()); item.put("semanticStatus", rule.semanticStatus());
            item.put("targetStatus", rule.targetStatus()); item.put("targetPath", rule.targetPath());
            item.put("operator", rule.operator()); item.put("expectedValues", rule.expectedValues());
            item.put("expectedSource", rule.expectedSource()); item.put("confidence", rule.confidence());
            item.put("evidence", rule.evidence());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> mapDiagnostics(List<CandidateOutputDiagnostic> diagnostics) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CandidateOutputDiagnostic diagnostic : diagnostics) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", diagnostic.code()); item.put("message", diagnostic.message());
            item.put("candidateId", diagnostic.candidateId()); item.put("ruleId", diagnostic.ruleId());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildWarnings(List<IngestionWarning> warnings) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (IngestionWarning warning : warnings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", warning.warningCode());
            item.put("message", warning.message());
            item.put("severity", severityOf(warning.severity()));
            if (warning.relatedPath() != null) {
                item.put("location", warning.relatedPath());
            }
            if (!warning.details().isEmpty()) {
                item.put("details", warning.details());
            }
            items.add(item);
        }
        return items;
    }

    private String severityOf(String severity) {
        if (severity == null) {
            return "warning";
        }
        String normalized = severity.toLowerCase();
        if ("high".equals(normalized)) {
            return "error";
        }
        if ("low".equals(normalized)) {
            return "info";
        }
        return "warning";
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
        List<Map<String, Object>> requestPreconditions,
        List<Map<String, Object>> responseAssertions,
        List<Map<String, Object>> excludedBusinessRules,
        Set<String> bodyFieldNames
    ) {}

}
