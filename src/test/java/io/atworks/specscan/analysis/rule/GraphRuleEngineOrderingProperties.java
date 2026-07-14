package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.rule.*;
import java.util.*;
import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

class GraphRuleEngineOrderingProperties {
    @Property(tries = 200, generation = GenerationMode.RANDOMIZED)
    void registrationOrderDoesNotChangeNormalizedResult(@ForAll boolean reverse) {
        GraphRule first = RuleTestFixtures.rule("alpha",
            RuleTestFixtures.ruleCandidate("alpha", BusinessRuleCategory.EXISTENCE, "condition"));
        GraphRule second = RuleTestFixtures.rule("beta",
            RuleTestFixtures.ruleCandidate("beta", BusinessRuleCategory.AUTHORIZATION, "condition"));
        List<GraphRule> ordered = reverse ? List.of(second, first) : List.of(first, second);
        List<GraphRule> opposite = reverse ? List.of(first, second) : List.of(second, first);

        GraphRuleEngineResult left = engine(ordered).evaluate(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());
        GraphRuleEngineResult right = engine(opposite).evaluate(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());

        assertThat(left.candidates().businessRules()).extracting(BusinessRuleCandidate::candidateId)
            .containsExactlyElementsOf(right.candidates().businessRules().stream().map(BusinessRuleCandidate::candidateId).toList());
        assertThat(left.report()).isEqualTo(right.report());
    }
    private DefaultGraphRuleEngine engine(List<GraphRule> rules) {
        return new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            List.of(new RulePack("pack", true, rules, RulePrecedence.none())));
    }
}
