package io.atworks.specscan.analysis.support.rule.yaml;

import io.atworks.specscan.analysis.domain.rule.*;
import java.nio.file.Path;
import java.util.*;

public final class YamlRulePackComposer {
    public static final String USER_PACK_ID = "user-yaml";
    private final YamlRulePackLoader loader;

    public YamlRulePackComposer() { this(new YamlRulePackLoader()); }
    YamlRulePackComposer(YamlRulePackLoader loader) { this.loader = loader; }

    public ConfiguredRulePacks compose(List<RulePack> builtInPacks, Path yamlPath) {
        return compose(builtInPacks, loader.load(yamlPath));
    }

    public ConfiguredRulePacks compose(List<RulePack> builtInPacks, String yaml) {
        return compose(builtInPacks, loader.load(yaml));
    }

    private ConfiguredRulePacks compose(List<RulePack> builtInPacks, YamlRuleLoadResult loaded) {
        List<RulePack> packs = new ArrayList<>(List.copyOf(builtInPacks));
        List<YamlRuleDiagnostic> diagnostics = new ArrayList<>(loaded.diagnostics());
        Set<String> knownIds = new HashSet<>();
        builtInPacks.stream().flatMap(pack -> pack.rules().stream()).map(GraphRule::id).forEach(knownIds::add);
        List<GraphRule> userRules = new ArrayList<>();
        for (YamlRuleDefinition definition : loaded.rules()) {
            if (!knownIds.add(definition.id())) {
                diagnostics.add(new YamlRuleDiagnostic(definition.id(), "DUPLICATE_RULE_ID",
                    "rule id conflicts with an already registered rule"));
            } else userRules.add(new YamlGraphRule(definition));
        }
        if (!userRules.isEmpty()) packs.add(new RulePack(USER_PACK_ID, true, userRules, RulePrecedence.none()));
        return new ConfiguredRulePacks(packs, userRules.size(), diagnostics);
    }
}
