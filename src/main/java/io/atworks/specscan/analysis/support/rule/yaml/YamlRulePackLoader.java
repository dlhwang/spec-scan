package io.atworks.specscan.analysis.support.rule.yaml;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.atworks.specscan.analysis.domain.candidate.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class YamlRulePackLoader {
    private static final Set<String> ROOT_FIELDS = Set.of("rules");
    private static final Set<String> RULE_FIELDS = Set.of("id", "match", "output");
    private static final Set<String> MATCH_FIELDS = Set.of(
        "predicateType", "methodName", "resolvedSignatureContains", "failureOutcome");
    private static final Set<String> OUTPUT_FIELDS = Set.of("category", "constraint");
    private static final Set<String> CONSTRAINT_FIELDS = Set.of("kind");
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public YamlRuleLoadResult load(Path path) {
        try {
            return load(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return failed("YAML_READ_ERROR", exception.getMessage());
        }
    }

    public YamlRuleLoadResult load(String yaml) {
        JsonNode root;
        try {
            root = mapper.readTree(yaml);
        } catch (Exception exception) {
            return failed("YAML_SYNTAX_ERROR", exception.getMessage());
        }
        if (root == null || !root.isObject()) return failed("INVALID_ROOT", "YAML root must be an object");
        List<YamlRuleDiagnostic> diagnostics = new ArrayList<>();
        rejectUnknown(root, ROOT_FIELDS, null, diagnostics);
        JsonNode entries = root.get("rules");
        if (entries == null || !entries.isArray()) return failed("RULES_REQUIRED", "rules must be an array");

        List<YamlRuleDefinition> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonNode node = entries.get(index);
            String fallbackId = "rules[" + index + "]";
            String id = text(node, "id");
            String diagnosticId = id == null ? fallbackId : id;
            int before = diagnostics.size();
            if (!node.isObject()) {
                diagnostics.add(new YamlRuleDiagnostic(fallbackId, "INVALID_RULE", "rule must be an object"));
                continue;
            }
            rejectUnknown(node, RULE_FIELDS, diagnosticId, diagnostics);
            if (id == null) diagnostics.add(new YamlRuleDiagnostic(diagnosticId, "RULE_ID_REQUIRED", "id is required"));
            else if (!ids.add(id)) diagnostics.add(new YamlRuleDiagnostic(id, "DUPLICATE_RULE_ID", "duplicate rule id"));
            JsonNode match = node.get("match");
            JsonNode output = node.get("output");
            if (match == null || !match.isObject()) diagnostics.add(new YamlRuleDiagnostic(diagnosticId, "MATCH_REQUIRED", "match is required"));
            if (output == null || !output.isObject()) diagnostics.add(new YamlRuleDiagnostic(diagnosticId, "OUTPUT_REQUIRED", "output is required"));
            if (match == null || !match.isObject() || output == null || !output.isObject()) continue;
            rejectUnknown(match, MATCH_FIELDS, diagnosticId, diagnostics);
            rejectUnknown(output, OUTPUT_FIELDS, diagnosticId, diagnostics);
            JsonNode constraint = output.get("constraint");
            if (constraint == null || !constraint.isObject()) {
                diagnostics.add(new YamlRuleDiagnostic(diagnosticId, "CONSTRAINT_REQUIRED", "output.constraint is required"));
            } else {
                rejectUnknown(constraint, CONSTRAINT_FIELDS, diagnosticId, diagnostics);
                if (!"CONTROL_FLOW_ONLY".equals(text(constraint, "kind")))
                    diagnostics.add(new YamlRuleDiagnostic(diagnosticId, "UNSUPPORTED_CONSTRAINT", "only CONTROL_FLOW_ONLY is supported"));
            }
            PredicateType predicateType = enumValue(PredicateType.class, text(match, "predicateType"), diagnosticId, "predicateType", diagnostics);
            YamlRuleDefinition.FailureOutcome failure = enumValue(YamlRuleDefinition.FailureOutcome.class,
                text(match, "failureOutcome"), diagnosticId, "failureOutcome", diagnostics);
            BusinessRuleCategory category = enumValue(BusinessRuleCategory.class, text(output, "category"), diagnosticId, "category", diagnostics);
            String methodName = text(match, "methodName");
            String signature = text(match, "resolvedSignatureContains");
            if (methodName == null && signature == null)
                diagnostics.add(new YamlRuleDiagnostic(diagnosticId, "CALL_MATCH_REQUIRED", "methodName or resolvedSignatureContains is required"));
            if (diagnostics.size() == before && id != null)
                rules.add(new YamlRuleDefinition(id, predicateType, methodName, signature, failure, category));
        }
        return new YamlRuleLoadResult(rules, diagnostics);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String ruleId,
                                                    String field, List<YamlRuleDiagnostic> diagnostics) {
        if (value == null) {
            diagnostics.add(new YamlRuleDiagnostic(ruleId, "FIELD_REQUIRED", field + " is required"));
            return null;
        }
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) {
            diagnostics.add(new YamlRuleDiagnostic(ruleId, "INVALID_ENUM", "invalid " + field + ": " + value));
            return null;
        }
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String ruleId,
                                      List<YamlRuleDiagnostic> diagnostics) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) diagnostics.add(new YamlRuleDiagnostic(ruleId, "UNKNOWN_FIELD", "unknown field: " + field));
        });
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.textValue().isBlank() ? null : value.textValue();
    }

    private static YamlRuleLoadResult failed(String code, String message) {
        return new YamlRuleLoadResult(List.of(), List.of(new YamlRuleDiagnostic(null, code, message)));
    }
}
