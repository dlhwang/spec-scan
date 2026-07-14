package io.atworks.specscan.analysis.support.rule.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlRulePackLoaderTest {
    private final YamlRulePackLoader loader = new YamlRulePackLoader();

    @Test
    void loadsMultipleRulesFromStringAndUtf8File(@TempDir Path tempDir) throws Exception {
        String yaml = validRule("PROJECT_VALIDATOR", "validateProject") + """
              - id: PROJECT_HELPER
                match:
                  predicateType: BOOLEAN_CALL
                  resolvedSignatureContains: com.example.ProjectHelper
                  failureOutcome: ELSE
                output:
                  category: STATE_PRECONDITION
                  constraint:
                    kind: CONTROL_FLOW_ONLY
            """;

        YamlRuleLoadResult fromString = loader.load(yaml);
        Path file = tempDir.resolve("사용자-rules.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        YamlRuleLoadResult fromFile = loader.load(file);

        assertThat(fromString.diagnostics()).isEmpty();
        assertThat(fromString.rules()).hasSize(2);
        assertThat(fromFile).isEqualTo(fromString);
    }

    @Test
    void keepsValidRuleWhenAnotherRuleIsInvalid() {
        String yaml = validRule("VALID_RULE", "validateProject") + """
              - id: INVALID_RULE
                match:
                  predicateType: BOOLEAN_CALL
                  methodName: other
                  failureOutcome: THEN
                output:
                  category: INVARIANT
                  constraint:
                    kind: INPUT_LITERAL
            """;

        YamlRuleLoadResult result = loader.load(yaml);

        assertThat(result.rules()).extracting(YamlRuleDefinition::id).containsExactly("VALID_RULE");
        assertThat(result.diagnostics()).extracting(YamlRuleDiagnostic::code)
            .containsExactly("UNSUPPORTED_CONSTRAINT");
    }

    @Test
    void reportsSyntaxAndDuplicateIdsWithoutThrowing() {
        assertThat(loader.load("rules: [").diagnostics()).extracting(YamlRuleDiagnostic::code)
            .containsExactly("YAML_SYNTAX_ERROR");

        String yaml = validRule("SAME_ID", "one") + validRuleEntry("SAME_ID", "two");
        YamlRuleLoadResult result = loader.load(yaml);

        assertThat(result.rules()).extracting(YamlRuleDefinition::id).containsExactly("SAME_ID");
        assertThat(result.diagnostics()).extracting(YamlRuleDiagnostic::code).contains("DUPLICATE_RULE_ID");
    }

    @Test
    void rejectsFieldsThatWouldInventAConstraint() {
        String yaml = validRule("SAFE_RULE", "validateProject").replace(
            "kind: CONTROL_FLOW_ONLY", "kind: CONTROL_FLOW_ONLY\n        targetPath: request.id\n        operator: EQ");

        YamlRuleLoadResult result = loader.load(yaml);

        assertThat(result.rules()).isEmpty();
        assertThat(result.diagnostics()).extracting(YamlRuleDiagnostic::code)
            .containsExactly("UNKNOWN_FIELD", "UNKNOWN_FIELD");
    }

    private static String validRule(String id, String method) {
        return "rules:\n" + validRuleEntry(id, method);
    }

    private static String validRuleEntry(String id, String method) {
        return """
              - id: %s
                match:
                  predicateType: BOOLEAN_CALL
                  methodName: %s
                  failureOutcome: THEN
                output:
                  category: INVARIANT
                  constraint:
                    kind: CONTROL_FLOW_ONLY
            """.formatted(id, method);
    }
}
