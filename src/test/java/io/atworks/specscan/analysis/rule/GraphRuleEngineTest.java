package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.rule.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GraphRuleEngineTest {
    @Test void preservesUnmatchedPredicateAsUnresolved() {
        GraphRuleEngineResult result = engine(List.of()).evaluate(
            RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());
        assertThat(result.candidates().predicates()).hasSize(1);
        assertThat(result.candidates().businessRules()).singleElement().satisfies(candidate -> {
            assertThat(candidate.semanticStatus()).isEqualTo(SemanticStatus.UNRESOLVED);
            assertThat(candidate.category()).isEqualTo(BusinessRuleCategory.UNKNOWN);
        });
        assertThat(result.report().registeredRules()).isZero();
    }

    @Test void isolatesFailingRuleAndKeepsSuccessfulMatch() {
        BusinessRuleCandidate good = RuleTestFixtures.ruleCandidate("good", BusinessRuleCategory.EXISTENCE, "condition");
        GraphRuleEngineResult result = engine(List.of(RuleTestFixtures.throwingRule("broken"),
            RuleTestFixtures.rule("good", good))).evaluate(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());
        assertThat(result.candidates().businessRules()).extracting(BusinessRuleCandidate::ruleId).containsExactly("good");
        assertThat(result.report().failedRuleExecutions()).isEqualTo(1);
        assertThat(result.report().diagnostics()).extracting(RuleExecutionDiagnostic::code).contains("RULE_EXECUTION_FAILED");
    }

    @Test void deduplicatesExactMatchAndAnnotatesAmbiguityAndPrecedence() {
        BusinessRuleCandidate state = RuleTestFixtures.ruleCandidate("state", BusinessRuleCategory.STATE_PRECONDITION, "condition");
        BusinessRuleCandidate auth = RuleTestFixtures.ruleCandidate("auth", BusinessRuleCategory.AUTHORIZATION, "condition");
        RulePack pack = new RulePack("pack", true, List.of(RuleTestFixtures.rule("state", state, state),
            RuleTestFixtures.rule("auth", auth)), new RulePrecedence(Map.of("auth", 2, "state", 1)));
        GraphRuleEngineResult result = new DefaultGraphRuleEngine(
            new DefaultValidationCandidateDetector(List.of()), List.of(pack)).evaluate(
                RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());

        assertThat(result.candidates().businessRules()).hasSize(2);
        assertThat(result.report().deduplicatedCandidates()).isEqualTo(1);
        BusinessRuleCandidate lower = result.candidates().businessRules().stream()
            .filter(candidate -> candidate.ruleId().equals("state")).findFirst().orElseThrow();
        assertThat(lower.diagnostics()).extracting(CandidateDiagnostic::code)
            .contains("AMBIGUOUS_RULE_MATCH", "LOWER_PRECEDENCE_MATCH");
        assertThat(result.candidates().businessRules()).extracting(BusinessRuleCandidate::ruleId).contains("auth", "state");
    }

    @Test void rejectsDuplicateRuleIdsAcrossPacks() {
        GraphRule duplicate = RuleTestFixtures.rule("same");
        assertThatThrownBy(() -> new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()), List.of(
            new RulePack("one", true, List.of(duplicate), RulePrecedence.none()),
            new RulePack("two", true, List.of(duplicate), RulePrecedence.none()))))
            .hasMessageContaining("DUPLICATE_RULE_ID");
    }

    @Test void isolatesNullRuleResultAndFallsBackToUnresolved() {
        GraphRule nullRule = new GraphRule() {
            public String id() { return "null-rule"; }
            public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }
            public List<BusinessRuleCandidate> match(io.atworks.specscan.analysis.domain.fact.FactCodeGraph graph,
                    PredicateCandidate candidate) { return null; }
        };
        GraphRuleEngineResult result = engine(List.of(nullRule)).evaluate(
            RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());
        assertThat(result.report().diagnostics()).extracting(RuleExecutionDiagnostic::code).contains("RULE_RETURNED_NULL");
        assertThat(result.candidates().businessRules()).singleElement()
            .extracting(BusinessRuleCandidate::semanticStatus).isEqualTo(SemanticStatus.UNRESOLVED);
    }

    private DefaultGraphRuleEngine engine(List<GraphRule> rules) {
        return new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            List.of(new RulePack("test", true, rules, RulePrecedence.none())));
    }
}
