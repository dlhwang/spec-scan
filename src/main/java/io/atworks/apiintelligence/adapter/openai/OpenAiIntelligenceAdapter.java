package io.atworks.apiintelligence.adapter.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.intelligence.ApiIntelligence;
import io.atworks.apiintelligence.domain.intelligence.IntelligenceItem;
import io.atworks.apiintelligence.port.out.IntelligenceModelPort;
import io.atworks.apiintelligence.port.out.FactIntelligenceModelPort;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenAiIntelligenceAdapter implements IntelligenceModelPort,
    FactIntelligenceModelPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiIntelligenceAdapter.class);

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final int maxInputCharacters;

    public OpenAiIntelligenceAdapter(URI endpoint, String apiKey, String model) {
        this(endpoint, apiKey, model, 60000);
    }

    public OpenAiIntelligenceAdapter(URI endpoint, String apiKey, String model, int maxInputCharacters) {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.endpoint = endpoint;
        this.apiKey = Objects.requireNonNull(apiKey);
        this.model = model == null ? "gpt-4o-mini" : model;
        this.maxInputCharacters = maxInputCharacters <= 0 ? 60000 : maxInputCharacters;
    }

    @Override
    public ApiIntelligence analyze(String apiId, CodeGraph graph, List<Evidence> evidence) {
        return analyzeGraph(apiId, graph, evidence);
    }

    @Override
    public ApiIntelligence analyze(String apiId, FactCodeGraph graph, List<Evidence> evidence) {
        return analyzeGraph(apiId, graph, evidence);
    }

    private ApiIntelligence analyzeGraph(String apiId, Object graph, List<Evidence> evidence) {
        try {
            var body = mapper.createObjectNode();
            body.put("model", model);
            body.put("instructions",
                """
                    Return exactly one valid JSON object and nothing else.
    
                    Use these arrays only:

                    * requestPreconditions
                    * responseAssertions
                    * businessRules
                    * exceptions

                    Each item:

                    {
                    "targetLocation": "PATH | QUERY | HEADER | REQUEST_BODY | STATUS | RESPONSE_HEADER | RESPONSE_BODY | SERVICE | DOMAIN | UNKNOWN",
                    "targetPath": "JSONPath or null",
                    "operator": "EQ | NEQ | GT | GTE | LT | LTE | CONTAINS | NOT_CONTAINS | EMPTY | NOT_EMPTY | NULL | NOT_NULL | SIZE | NOT_BLANK | PATTERN | EMAIL | MIN_AGE",
                    "expected": "JSON value or null",
                    "expectedSource": "source expression",
                    "ruleId": "UPPER_SNAKE_CASE",
                    "evidenceIds": ["supplied IDs only"],
                    "confidence": 0.0
                    }

                    Constraints:

                    * Use only the listed operators.
                    * Preserve JSON types. Return 204, not "204".
                    * STATUS uses targetPath=null.
                    * Unary operators use expected=null.
                    * Do not infer unsupported rules.
                    * @ApiResponse proves only a documented status.
                    * ResponseEntity.ok means 200.
                    * ResponseEntity.noContent means 204.
                    * Return empty arrays when no evidence exists.
                """

//                "You are an API code intelligence extractor. Return exactly one valid JSON object and nothing else. Do not add explanations, preambles, Markdown fences, or code fences. Use these top-level keys only: preConditions, responseAssertions, businessRules, exceptions, additionalAnalysis. Each key must contain an array of objects with description, category, evidenceIds, and confidence. evidenceIds must refer only to supplied Evidence IDs."
            );
            String inputText = "Analyze exactly one API and return the required JSON object. API="
                + apiId + " GRAPH=" + mapper.writeValueAsString(graph) + " EVIDENCE="
                + mapper.writeValueAsString(evidence);
            if (inputText.length() > maxInputCharacters) {
                inputText = inputText.substring(0, maxInputCharacters) + "...[TRUNCATED]";
            }
            body.put("input", inputText);
            var req = HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new OpenAiAdapterException("OpenAI HTTP " + res.statusCode() + ": " + res.body());
            }
            return parse(res.body(), apiId, evidence);
        } catch (OpenAiAdapterException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiAdapterException("OpenAI call failed", e);
        }
    }

    private ApiIntelligence parse(String json, String apiId, List<Evidence> evidence)
        throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode n = root.has("result") ? root.get("result") : root;
        if (root.has("output_text")) {
            n = mapper.readTree(extractJson(stripMarkdownJson(root.get("output_text").asText())));
        } else if (root.has("output") && root.get("output").isArray() && !root.get("output")
            .isEmpty()) {
            String t = root.at("/output/0/content/0/text").asText("");
            if (!t.isBlank()) {
                n = mapper.readTree(extractJson(stripMarkdownJson(t)));
            }
        }
        String fallbackEvidence =
            evidence.isEmpty() ? "model-output" : evidence.get(0).evidenceId();
        String preconditionField = n.has("requestPreconditions")
            ? "requestPreconditions" : "preConditions";
        return new ApiIntelligence(apiId, items(n, preconditionField, fallbackEvidence),
            items(n, "responseAssertions", fallbackEvidence),
            items(n, "businessRules", fallbackEvidence), items(n, "exceptions", fallbackEvidence),
            items(n, "additionalAnalysis", fallbackEvidence));
    }

    private List<IntelligenceItem> items(JsonNode n, String field, String fallbackEvidence) {
        List<IntelligenceItem> out = new ArrayList<>();
        JsonNode value = n.path(field);
        if (value.isArray()) {
            value.forEach(x -> {
                try {
                    out.add(item(x, fallbackEvidence));
                } catch (Exception e) {
                    log.warn("Failed to parse intelligence item from {}: {}", x, e.getMessage());
                }
            });
        } else if (value.isObject()) {
            value.fields().forEachRemaining(
                e -> {
                    String desc = safeText(e.getKey(), "") + ": " + scalar(e.getValue());
                    if (desc.isBlank()) desc = "field: " + field;
                    out.add(new IntelligenceItem(desc, "analysis", List.of(fallbackEvidence), 0.5));
                });
        }
        return out;
    }

    private IntelligenceItem item(JsonNode x, String fallbackEvidence) {
        String description = safeText(x, "description", "");
        if (description.isBlank()) {
            description = safeText(x, "ruleId", scalar(x));
        }
        if (description.isBlank()) {
            description = "API Intelligence Item";
        }

        String category = safeText(x, "ruleId", safeText(x, "category", "LLM_ANALYSIS"));
        String targetLocation = safeText(x, "targetLocation", "UNKNOWN");
        String targetPath = safeText(x, "targetPath", null);
        String operator = safeText(x, "operator", "EQ");
        String expectedSource = safeText(x, "expectedSource", "LLM");

        List<String> ids = toStrings(x.path("evidenceIds")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        if (ids.isEmpty()) {
            ids = List.of(fallbackEvidence);
        }

        Object expected = x.has("expected") && !x.get("expected").isNull()
            ? mapper.convertValue(x.get("expected"), Object.class) : null;
        double confidence = x.path("confidence").asDouble(0.5);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            confidence = 0.5;
        }

        return new IntelligenceItem(description, category, targetLocation,
            targetPath, operator, expected, expectedSource, ids, confidence);
    }

    private String safeText(JsonNode node, String fieldName, String fallback) {
        if (node == null) return fallback;
        JsonNode v = node.path(fieldName);
        if (v.isMissingNode() || v.isNull()) return fallback;
        String s = v.asText("").trim();
        return s.isEmpty() ? fallback : s;
    }

    private String safeText(String value, String fallback) {
        if (value == null) return fallback;
        String s = value.trim();
        return s.isEmpty() ? fallback : s;
    }

    private String scalar(JsonNode n) {
        return n.isValueNode() ? n.asText() : n.toString();
    }

    private String stripMarkdownJson(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int closing = value.lastIndexOf("```");
            if (firstNewline >= 0 && closing > firstNewline) {
                value = value.substring(firstNewline + 1, closing).trim();
            }
        }
        return value;
    }

    private String extractJson(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("{") || value.startsWith("[")) {
            return value;
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private List<String> toStrings(JsonNode n) {
        List<String> r = new ArrayList<>();
        n.forEach(x -> r.add(x.asText()));
        return r;
    }
}
