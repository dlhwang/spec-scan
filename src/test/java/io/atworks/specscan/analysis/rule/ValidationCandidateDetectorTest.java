package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.fixture.ContractDetailValidationFixture;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.rule.DefaultValidationCandidateDetector;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class ValidationCandidateDetectorTest {
    @TempDir Path workspace;

    @Test void promotesDelegatedBooleanValidationFailures() throws Exception {
        var graph = ContractDetailValidationFixture.buildPostGraph(workspace);
        Set<String> methodIds = graph.nodes().stream()
            .filter(node -> node.type() == FactNodeType.API_METHOD
                || node.type() == FactNodeType.METHOD || node.type() == FactNodeType.CONSTRUCTOR)
            .map(FactNode::id).collect(Collectors.toCollection(LinkedHashSet::new));
        var detected = new DefaultValidationCandidateDetector(List.of()).detectReported(graph,
            new MethodScope(graph.graphId(), methodIds));
        Map<String, FactNode> nodes = graph.nodes().stream()
            .collect(Collectors.toMap(FactNode::id, node -> node));
        List<String> predicates = detected.candidates().stream()
            .map(candidate -> nodes.get(candidate.conditionNodeId()))
            .filter(java.util.Objects::nonNull).map(FactNode::snippet).toList();

        assertThat(predicates).withFailMessage("predicates=%s", predicates)
            .anyMatch(value -> value.contains("contractType == null"))
            .anyMatch(value -> value.contains("contractType == JEONSE")
                && value.contains("deposit <= 0"))
            .anyMatch(value -> value.contains("contractType == MONTHLYRENT")
                && value.contains("rent <= 0") && value.contains("deposit <= 0"));
        assertThat(detected.candidates()).allSatisfy(candidate -> assertThat(candidate.evidence())
            .extracting(EvidenceRef::role)
            .contains(EvidenceRole.PREDICATE, EvidenceRole.CALL, EvidenceRole.FAILURE_OUTCOME));
    }

    @Test void detectsThrowButNotOrdinaryReturn() {
        DefaultValidationCandidateDetector detector = new DefaultValidationCandidateDetector(List.of());
        assertThat(detector.detect(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope())).hasSize(1)
            .first().extracting(PredicateCandidate::conditionNodeId).isEqualTo("condition");
        assertThat(detector.detect(RuleTestFixtures.graph(FactNodeType.RETURN), RuleTestFixtures.scope())).isEmpty();
    }

    @Test void promotesTrueFromPositiveInvalidGuard() {
        assertThat(new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.delegatedBooleanGraph(false, true, true), RuleTestFixtures.scope()))
            .extracting(PredicateCandidate::conditionNodeId).containsExactly("inner");
    }

    @Test void promotesFalseFromNegatedValidGuard() {
        assertThat(new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.delegatedBooleanGraph(true, false, true), RuleTestFixtures.scope()))
            .extracting(PredicateCandidate::conditionNodeId).containsExactly("inner");
    }

    @Test void doesNotPromoteBooleanHelperWithoutFailureOutcome() {
        assertThat(new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.delegatedBooleanGraph(false, true, false), RuleTestFixtures.scope())).isEmpty();
    }

    @Test void stopsDelegatedPropagationBeyondDepthTwoAsPartial() {
        assertThat(new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.delegatedDepthGraph(false), RuleTestFixtures.scope()))
            .singleElement().satisfies(candidate -> {
                assertThat(candidate.extractionStatus()).isEqualTo(ExtractionStatus.PARTIAL);
                assertThat(candidate.diagnostics()).extracting(CandidateDiagnostic::code)
                    .contains("DELEGATED_MAX_DEPTH_EXCEEDED");
            });
    }

    @Test void stopsDelegatedCallCycleWithoutDroppingCandidate() {
        assertThat(new DefaultValidationCandidateDetector(List.of()).detect(
            RuleTestFixtures.delegatedDepthGraph(true), RuleTestFixtures.scope()))
            .singleElement().satisfies(candidate -> {
                assertThat(candidate.extractionStatus()).isEqualTo(ExtractionStatus.PARTIAL);
                assertThat(candidate.diagnostics()).extracting(CandidateDiagnostic::code)
                    .contains("DELEGATED_CALL_CYCLE");
            });
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

    @Test void isolatesSeedContributorExceptionInDetectionReport() {
        ValidationSeedContributor broken = new ValidationSeedContributor() {
            public String id() { return "broken-seed"; }
            public List<PredicateCandidate> contribute(
                    io.atworks.specscan.analysis.domain.fact.FactCodeGraph graph,
                    MethodScope scope, List<PredicateCandidate> existingCandidates) {
                throw new IllegalStateException("broken");
            }
        };
        CandidateDetectionResult result = new DefaultValidationCandidateDetector(List.of(), List.of(broken))
            .detectReported(RuleTestFixtures.graph(FactNodeType.THROW), RuleTestFixtures.scope());

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("SEED_CONTRIBUTOR_FAILED");
            assertThat(diagnostic.ruleId()).isEqualTo("broken-seed");
        });
    }
}
