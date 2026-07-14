package io.atworks.specscan.analysis.support.rule.yaml;

import java.util.List;

public record YamlRuleLoadResult(List<YamlRuleDefinition> rules, List<YamlRuleDiagnostic> diagnostics) {
    public YamlRuleLoadResult {
        rules = List.copyOf(rules);
        diagnostics = List.copyOf(diagnostics);
    }
}
