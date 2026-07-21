package io.atworks.apiintelligence.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts bounded-context intelligence output into the existing /api/scan response contract.
 */
public final class LlmScanResponseAdapter {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> toScanResponse(Map<String, Object> analysis) {
        JsonNode root = mapper.valueToTree(analysis);
        Map<String, JsonNode> intelligence = new LinkedHashMap<>();
        root.path("intelligence").fields()
            .forEachRemaining(e -> intelligence.put(e.getKey(), e.getValue()));
        Map<String, JsonNode> evidenceIndex = new LinkedHashMap<>();
        root.path("evidence").fields().forEachRemaining(api -> api.getValue().forEach(e ->
            evidenceIndex.put(e.path("evidenceId").asText(), e)));
        List<Map<String, Object>> operations = new ArrayList<>();
        for (JsonNode api : root.path("apis")) {
            String id = api.path("apiId").asText();
            JsonNode result = intelligence.getOrDefault(id, mapper.createObjectNode());
            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("operationId", id);
            operation.put("method", api.path("httpMethod").asText());
            operation.put("path", api.path("path").asText());
            operation.put("controllerClass", api.path("controllerType").asText());
            operation.put("request", emptyRequest());
            operation.put("response200", emptyResponse(api.path("responseType").asText()));
            operation.put("requestPreconditions", conditions(result, "preConditions", evidenceIndex));
            operation.put("responseAssertions", conditions(result, "responseAssertions", evidenceIndex));
            operation.put("excludedBusinessRules", conditions(result, "businessRules", evidenceIndex));
            operations.add(operation);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operations", operations);
        response.put("analysisMode", "LLM");
        response.put("runDirectory", root.path("runDirectory").asText());
        response.put("rawIntelligence", analysis.get("intelligence"));
        response.put("graphs", analysis.get("graphs"));
        response.put("evidence", analysis.get("evidence"));
        return response;
    }

    private List<JsonNode> array(JsonNode parent, String field) {
        List<JsonNode> values = new ArrayList<>();
        parent.path(field).forEach(values::add);
        return values;
    }

    private List<Map<String, Object>> conditions(JsonNode parent, String field,
        Map<String, JsonNode> evidenceIndex) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (JsonNode item : array(parent, field)) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("targetLocation", item.path("targetLocation").asText("UNKNOWN"));
            mapped.put("targetPath", item.path("targetPath").isNull()
                ? null : item.path("targetPath").asText(null));
            mapped.put("operator", item.path("operator").asText("EQ"));
            mapped.put("expected", item.has("expected") && !item.get("expected").isNull()
                ? mapper.convertValue(item.get("expected"), Object.class) : null);
            mapped.put("expectedSource", item.path("expectedSource").asText("LLM"));
            mapped.put("ruleId", item.path("category").asText("LLM_ANALYSIS"));
            mapped.put("confidence", item.path("confidence").asDouble(0.5));
            mapped.put("evidence", evidenceRefs(item.path("evidenceIds"), evidenceIndex));
            values.add(mapped);
        }
        return values;
    }

    private List<Map<String, Object>> evidenceRefs(JsonNode ids, Map<String, JsonNode> index) {
        List<Map<String, Object>> refs = new ArrayList<>();
        ids.forEach(id -> {
            JsonNode evidence = index.get(id.asText());
            if (evidence == null) return;
            JsonNode location = evidence.path("sourceLocation");
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("nodeId", evidence.path("graphNodeIds").path(0).asText(id.asText()));
            ref.put("filePath", location.path("relativePath").asText("unknown"));
            ref.put("startLine", location.path("startLine").asInt(1));
            ref.put("startColumn", location.path("startColumn").asInt(1));
            ref.put("endLine", location.path("endLine").asInt(1));
            ref.put("endColumn", location.path("endColumn").asInt(1));
            ref.put("role", "PRIMARY"); ref.put("snippet", evidence.path("snippet").asText(""));
            refs.add(ref);
        });
        if (refs.isEmpty() && ids.isArray() && !ids.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("nodeId", ids.path(0).asText("model-output")); fallback.put("filePath", "model-output");
            fallback.put("startLine", 1); fallback.put("startColumn", 1); fallback.put("endLine", 1);
            fallback.put("endColumn", 1); fallback.put("role", "PRIMARY"); fallback.put("snippet", "LLM analysis output");
            refs.add(fallback);
        }
        return refs;
    }

    private Map<String, Object> emptyRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contentType", null); request.put("pathParams", List.of());
        request.put("queryParams", List.of()); request.put("headers", List.of());
        request.put("bodySchema", null); request.put("bodyExample", null);
        return request;
    }

    private Map<String, Object> emptyResponse(String returnType) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("contentType", "String".equals(returnType) ? "text/plain" : "application/json");
        response.put("schema", null); response.put("example", null);
        return response;
    }
}
