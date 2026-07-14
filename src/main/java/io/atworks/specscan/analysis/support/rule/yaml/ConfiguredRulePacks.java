package io.atworks.specscan.analysis.support.rule.yaml;

import io.atworks.specscan.analysis.domain.rule.RulePack;
import java.util.List;

public record ConfiguredRulePacks(List<RulePack> packs, int loadedUserRuleCount,
                                  List<YamlRuleDiagnostic> diagnostics) {
    public ConfiguredRulePacks {
        packs = List.copyOf(packs);
        diagnostics = List.copyOf(diagnostics);
    }
}
