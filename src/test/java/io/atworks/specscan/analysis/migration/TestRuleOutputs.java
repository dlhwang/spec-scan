package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.application.RuleOutputService;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import java.util.List;
import java.util.Map;

final class TestRuleOutputs {
    private TestRuleOutputs() {}

    static Map<String, EndpointRuleOutput> generate(StaticScanResult scan, RepositorySource source) {
        return generate(scan, source, List.of());
    }

    static Map<String, EndpointRuleOutput> generate(StaticScanResult scan, RepositorySource source,
                                                    List<ApiConditionDraft> annotationConditions) {
        FactGraphBuildResult build = new DefaultFactCodeGraphBuilder().build(scan, source,
            FactGraphTraversalBudget.defaults());
        return new RuleOutputService().generate(scan, build, annotationConditions);
    }
}
