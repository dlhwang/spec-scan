package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.rule.*;
import java.util.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import static org.assertj.core.api.Assertions.assertThat;

class RuleFailureIsolationProperties {
    @Property(tries = 200, generation = GenerationMode.RANDOMIZED)
    void oneFailingRuleDoesNotRemoveSuccessfulCandidates(
            @ForAll @IntRange(min = 1, max = 8) int successfulRules) {
        List<GraphRule> rules = new ArrayList<>();
        for (int i = 0; i < successfulRules; i++) {
            String id = "success-" + i;
            rules.add(RuleTestFixtures.rule(id,
                RuleTestFixtures.ruleCandidate(id, BusinessRuleCategory.INVARIANT, "condition")));
        }
        rules.add(RuleTestFixtures.throwingRule("failure"));
        DefaultGraphRuleEngine engine = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            List.of(new RulePack("pack", true, rules, RulePrecedence.none())));

        GraphRuleEngineResult result = engine.evaluate(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());
        assertThat(result.candidates().businessRules()).hasSize(successfulRules);
        assertThat(result.report().failedRuleExecutions()).isEqualTo(1);
        assertThat(result.report().matchedCandidates()).isEqualTo(successfulRules);
    }
}
