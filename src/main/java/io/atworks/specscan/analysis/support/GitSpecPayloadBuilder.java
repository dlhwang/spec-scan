package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GitSpecPayloadBuilder {

    public Map<String, Object> build(
        String projectName,
        String baseUrl,
        RepositorySource repositorySource,
        StaticScanResult scanResult,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> normalizedConditions,
        List<IngestionWarning> warnings
    ) {
        List<Map<String, Object>> operations = new ArrayList<>();
        List<Map<String, Object>> flattenedConditions = new ArrayList<>();

        for (ApiEndpoint endpoint : scanResult.endpoints()) {
            List<Map<String, Object>> endpointConditions = buildConditionsForEndpoint(endpoint, drafts, normalizedConditions);
            operations.add(buildOperation(baseUrl, endpoint, endpointConditions));
            flattenedConditions.addAll(endpointConditions);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document", buildDocument(projectName, baseUrl, scanResult));
        payload.put("analysis", buildAnalysis(repositorySource, scanResult, warnings));
        payload.put("operations", operations);
        payload.put("conditions", flattenedConditions);
        if (!warnings.isEmpty()) {
            payload.put("warnings", buildWarnings(warnings));
        }
        return payload;
    }

    private Map<String, Object> buildDocument(String projectName, String baseUrl, StaticScanResult scanResult) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", "3.0.3");
        document.put("info", Map.of(
            "title", projectName + " (Spec Scan)",
            "version", "git-analysis"
        ));
        document.put("servers", List.of(Map.of("url", baseUrl)));

        Map<String, Object> paths = new LinkedHashMap<>();
        for (ApiEndpoint endpoint : scanResult.endpoints()) {
            Map<String, Object> pathItem = castMap(paths.computeIfAbsent(endpoint.path(), _k -> new LinkedHashMap<>()));
            pathItem.put(endpoint.httpMethod().toLowerCase(), buildDocumentOperation(endpoint));
        }
        document.put("paths", paths);
        return document;
    }

    private Map<String, Object> buildDocumentOperation(ApiEndpoint endpoint) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("summary", endpoint.controllerMethod());
        operation.put("operationId", endpoint.controllerMethod());
        operation.put("tags", List.of(endpoint.controllerClass()));

        List<Map<String, Object>> parameters = new ArrayList<>();
        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.targetLocation() == BindingLocation.BODY) {
                continue;
            }
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("name", binding.parameterName());
            parameter.put("in", binding.targetLocation().name().toLowerCase());
            parameter.put("required", binding.isRequired());
            if (binding.description() != null && !binding.description().isBlank()) {
                parameter.put("description", binding.description());
            }

            Map<String, Object> schema = primitiveSchema(binding.type());
            if (binding.example() != null && !binding.example().isBlank()) {
                schema.put("example", binding.example());
                parameter.put("example", binding.example());
            }
            if (binding.defaultValue() != null && !binding.defaultValue().isBlank()) {
                schema.put("default", binding.defaultValue());
            }
            if (!binding.enumValues().isEmpty()) {
                schema.put("enum", binding.enumValues());
            }
            parameter.put("schema", schema);
            parameters.add(parameter);
        }
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }

        RequestBinding bodyBinding = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() == BindingLocation.BODY)
            .findFirst()
            .orElse(null);
        if (bodyBinding != null) {
            operation.put("requestBody", Map.of(
                "required", true,
                "content", Map.of(
                    "application/json", Map.of(
                        "schema", Map.of("type", "object")
                    )
                )
            ));
        }

        operation.put("responses", Map.of(
            "200", Map.of("description", "Success")
        ));
        return operation;
    }

    private Map<String, Object> buildAnalysis(RepositorySource repositorySource, StaticScanResult scanResult, List<IngestionWarning> warnings) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("endpointCount", scanResult.endpoints().size());
        analysis.put("fileCount", scanResult.scannedClassesCount());
        String refType = repositorySource.ingestionMetadata().requestedRefType();
        if (refType != null) {
            String normalized = refType.toLowerCase();
            if ("branch".equals(normalized) || "tag".equals(normalized) || "commit".equals(normalized)) {
                analysis.put("revisionType", normalized);
            }
        }
        String ref = repositorySource.ingestionMetadata().resolvedRef();
        if (ref != null) {
            analysis.put("revision", ref);
        }
        analysis.put("warningCount", warnings.size());
        analysis.put("warningSummary", warnings.stream().map(IngestionWarning::message).toList());
        return analysis;
    }

    private List<Map<String, Object>> buildWarnings(List<IngestionWarning> warnings) {
        List<Map<String, Object>> result = new ArrayList<>();
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
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildConditionsForEndpoint(
        ApiEndpoint endpoint,
        List<ApiConditionDraft> drafts,
        List<ApiCondition> normalizedConditions
    ) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        String endpointId = endpoint.httpMethod() + " " + endpoint.path();
        Set<String> parameterNames = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() != BindingLocation.BODY)
            .map(RequestBinding::parameterName)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
        boolean hasBody = endpoint.requestBindings().stream().anyMatch(binding -> binding.targetLocation() == BindingLocation.BODY);

        for (ApiConditionDraft draft : drafts) {
            if (hasBody || parameterNames.contains(draft.targetPath())) {
                conditions.add(buildConditionMap(
                    endpointId,
                    resolveTargetLocation(endpoint, draft.targetPath()),
                    draft.targetPath(),
                    draft.operator(),
                    draft.expected(),
                    "BEAN_VALIDATION_ANNOTATION",
                    draft.sourceTrace() == null ? null : draft.sourceTrace().fileRelativePath(),
                    null,
                    draft.sourceTrace() == null ? null : draft.sourceTrace().startLine(),
                    draft.evidence(),
                    "STATIC_ANALYZER"
                ));
            }
        }

        for (ApiCondition condition : normalizedConditions) {
            if (hasBody || parameterNames.contains(condition.targetPath())) {
                conditions.add(buildConditionMap(
                    endpointId,
                    resolveTargetLocation(endpoint, condition.targetPath()),
                    condition.targetPath(),
                    condition.operator(),
                    condition.expected(),
                    "SERVICE_LOGIC_HINT",
                    condition.sourceTrace() == null ? null : condition.sourceTrace().fileRelativePath(),
                    null,
                    condition.sourceTrace() == null ? null : condition.sourceTrace().startLine(),
                    condition.evidence(),
                    "RULE_BASED_NORMALIZER"
                ));
            }
        }
        return conditions;
    }

    private Map<String, Object> buildOperation(String baseUrl, ApiEndpoint endpoint, List<Map<String, Object>> conditions) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("endpoint", Map.of(
            "id", endpoint.httpMethod() + " " + endpoint.path(),
            "method", endpoint.httpMethod(),
            "path", endpoint.path(),
            "summary", endpoint.controllerMethod(),
            "operationId", endpoint.controllerMethod(),
            "tags", List.of(endpoint.controllerClass())
        ));
        operation.put("baseUrl", baseUrl);
        operation.put("parameters", buildOperationParameters(endpoint));
        operation.put("contentTypes", hasBody(endpoint) ? List.of("application/json") : List.of());
        if (hasBody(endpoint)) {
            operation.put("bodySchema", Map.of("type", "object"));
            operation.put("bodyDraft", Map.of());
        }
        operation.put("securityInputs", List.of());
        operation.put("conditions", conditions);
        return operation;
    }

    private List<Map<String, Object>> buildOperationParameters(ApiEndpoint endpoint) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.targetLocation() == BindingLocation.BODY) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", binding.parameterName());
            item.put("location", binding.targetLocation().name().toLowerCase());
            item.put("required", binding.isRequired());
            if (binding.description() != null && !binding.description().isBlank()) {
                item.put("description", binding.description());
            }
            if (binding.example() != null && !binding.example().isBlank()) {
                item.put("example", binding.example());
            }
            if (binding.defaultValue() != null && !binding.defaultValue().isBlank()) {
                item.put("defaultValue", binding.defaultValue());
            }
            Map<String, Object> schema = primitiveSchema(binding.type());
            if (binding.example() != null && !binding.example().isBlank()) {
                schema.put("example", binding.example());
            }
            if (binding.defaultValue() != null && !binding.defaultValue().isBlank()) {
                schema.put("default", binding.defaultValue());
            }
            if (!binding.enumValues().isEmpty()) {
                schema.put("enum", binding.enumValues());
            }
            if (binding.description() != null && !binding.description().isBlank()) {
                schema.put("description", binding.description());
            }
            item.put("schema", schema);
            parameters.add(item);
        }
        return parameters;
    }

    private Map<String, Object> buildConditionMap(
        String endpointId,
        String targetLocation,
        String targetPath,
        String operator,
        String expected,
        String evidenceSource,
        String sourceClass,
        String sourceMethod,
        Integer lineNumber,
        String evidence,
        String normalizedBy
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("endpointId", endpointId);
        item.put("purpose", "REQUEST_VALIDATION");
        item.put("targetLocation", targetLocation);
        item.put("targetPath", targetPath);
        item.put("operator", operator);
        item.put("expected", expected);
        item.put("evidenceSource", evidenceSource);
        item.put("normalizedBy", normalizedBy);
        item.put("confidence", "MEDIUM");
        item.put("sourceClass", sourceClass);
        item.put("sourceMethod", sourceMethod);
        item.put("lineNumber", lineNumber);
        item.put("evidence", evidence);
        item.put("active", true);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
