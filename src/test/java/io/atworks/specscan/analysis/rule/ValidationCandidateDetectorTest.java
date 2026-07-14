package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.rule.DefaultValidationCandidateDetector;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ValidationCandidateDetectorTest {
    @Test void detectsThrowButNotOrdinaryReturn() {
        DefaultValidationCandidateDetector detector = new DefaultValidationCandidateDetector(List.of());
        assertThat(detector.detect(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope())).hasSize(1)
            .first().extracting(PredicateCandidate::conditionNodeId).isEqualTo("condition");
        assertThat(detector.detect(RuleTestFixtures.graph(FactNodeType.RETURN), RuleTestFixtures.scope())).isEmpty();
    }

    @Test void policyCanIncludeReturnAsPartialFailure() {
        CandidateDiagnostic diagnostic = new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
            "RETURN_FAILURE_FALLBACK", "Return policy used structural fallback", "outcome");
        FailureOutcomePolicy policy = new FailureOutcomePolicy() {
            public String id() { return "return-false"; }
            public FailureOutcomeDecision evaluate(io.atworks.specscan.analysis.domain.fact.FactCodeGraph graph,
                    io.atworks.specscan.analysis.domain.fact.FactNode condition,
                    io.atworks.specscan.analysis.domain.fact.FactNode outcome) {
                return new FailureOutcomeDecision(true, ExtractionStatus.PARTIAL, List.of(diagnostic));
            }
        };
        PredicateCandidate candidate = new DefaultValidationCandidateDetector(List.of(policy))
            .detect(RuleTestFixtures.graph(FactNodeType.RETURN), RuleTestFixtures.scope()).get(0);
        assertThat(candidate.extractionStatus()).isEqualTo(ExtractionStatus.PARTIAL);
        assertThat(candidate.diagnostics()).extracting(CandidateDiagnostic::code).contains("RETURN_FAILURE_FALLBACK");
    }

    @Test void rejectsScopeOutsideGraph() {
        assertThatThrownBy(() -> new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.graph(FactNodeType.THROW), new MethodScope("other", java.util.Set.of("root"))))
            .hasMessageContaining("INVALID_METHOD_SCOPE");
    }

    @Test void detectsTwoSameKindValidationsInOneMethod() {
        assertThat(new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.graphWithTwoThrows(), RuleTestFixtures.scope()))
            .extracting(PredicateCandidate::conditionNodeId).containsExactly("condition-1", "condition-2");
    }

    @Test void isolatesFailureOutcomePolicyExceptionInDetectionReport() {
        FailureOutcomePolicy broken = new FailureOutcomePolicy() {
            public String id() { return "broken-policy"; }
            public FailureOutcomeDecision evaluate(io.atworks.specscan.analysis.domain.fact.FactCodeGraph graph,
                    io.atworks.specscan.analysis.domain.fact.FactNode condition,
                    io.atworks.specscan.analysis.domain.fact.FactNode outcome) { throw new IllegalStateException("broken"); }
        };
        CandidateDetectionResult result = new DefaultValidationCandidateDetector(List.of(broken)).detectReported(
            RuleTestFixtures.graph(FactNodeType.RETURN), RuleTestFixtures.scope());
        assertThat(result.candidates()).isEmpty();
        assertThat(result.diagnostics()).extracting(RuleExecutionDiagnostic::code).containsExactly("FAILURE_POLICY_FAILED");
    }
}
