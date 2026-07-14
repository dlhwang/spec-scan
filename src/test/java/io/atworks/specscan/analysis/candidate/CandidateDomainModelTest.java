package io.atworks.specscan.analysis.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CandidateDomainModelTest {
    private final EvidenceRef evidence = new EvidenceRef("condition-1", "src/Order.java", 10, 5, 10, 20,
        EvidenceRole.PREDICATE, "order.isCancelled()");
    private final CandidateDiagnostic unresolved = new CandidateDiagnostic(
        CandidateDiagnosticSeverity.WARNING, "RULE_UNRESOLVED", "No rule matched", "condition-1");

    @Test
    void resolvedRuleCanUseOtherAndNoDiagnostic() {
        NormalizedConstraint constraint = new NormalizedConstraint(
            ConstraintKind.INPUT_LITERAL, "status", "==", List.of("CANCELLED"), "enum constant");
        BusinessRuleCandidate candidate = new BusinessRuleCandidateFactory().create(
            "predicate-1", "state.custom", BusinessRuleCategory.OTHER, ExtractionStatus.EXTRACTED,
            SemanticStatus.RESOLVED, TargetResolutionStatus.RESOLVED, constraint, 0.9,
            List.of(evidence), List.of());

        assertThat(candidate.category()).isEqualTo(BusinessRuleCategory.OTHER);
        assertThat(candidate.diagnostics()).isEmpty();
    }

    @Test
    void unresolvedRuleRequiresUnknownAndDiagnostic() {
        BusinessRuleCandidate candidate = new BusinessRuleCandidateFactory().create(
            "predicate-1", null, BusinessRuleCategory.UNKNOWN, ExtractionStatus.EXTRACTED,
            SemanticStatus.UNRESOLVED, TargetResolutionStatus.UNRESOLVED, null, 0.0,
            List.of(evidence), List.of(unresolved));

        assertThat(candidate.ruleId()).isNull();
        assertThat(candidate.category()).isEqualTo(BusinessRuleCategory.UNKNOWN);
    }

    @Test
    void semanticResolutionAndTargetFailureRemainIndependent() {
        BusinessRuleCandidate candidate = new BusinessRuleCandidateFactory().create(
            "predicate-1", "state.precondition", BusinessRuleCategory.STATE_PRECONDITION,
            ExtractionStatus.EXTRACTED, SemanticStatus.RESOLVED, TargetResolutionStatus.UNRESOLVED,
            new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY, null, null, List.of(), null), 0.7,
            List.of(evidence), List.of(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
                "TARGET_UNRESOLVED", "Target could not be linked", "condition-1")));

        assertThat(candidate.semanticStatus()).isEqualTo(SemanticStatus.RESOLVED);
        assertThat(candidate.targetStatus()).isEqualTo(TargetResolutionStatus.UNRESOLVED);
    }

    @Test
    void invalidStatusCombinationIsRejected() {
        assertThatThrownBy(() -> new BusinessRuleCandidateFactory().create(
            "predicate-1", "rule", BusinessRuleCategory.UNKNOWN, ExtractionStatus.EXTRACTED,
            SemanticStatus.RESOLVED, TargetResolutionStatus.NOT_APPLICABLE, null, 0.8,
            List.of(evidence), List.of())).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVALID_STATUS_COMBINATION");
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        List<String> expected = new ArrayList<>(List.of("A"));
        NormalizedConstraint constraint = new NormalizedConstraint(
            ConstraintKind.INPUT_LITERAL, "status", "==", expected, "literal");
        expected.add("B");

        assertThat(constraint.expectedValues()).containsExactly("A");
        assertThatThrownBy(() -> constraint.expectedValues().add("C"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sameRuleCanProduceDistinctEvidenceAwareCandidateIds() {
        BusinessRuleCandidateFactory factory = new BusinessRuleCandidateFactory();
        BusinessRuleCandidate first = factory.create("predicate-1", "state.rule",
            BusinessRuleCategory.STATE_PRECONDITION, ExtractionStatus.EXTRACTED, SemanticStatus.RESOLVED,
            TargetResolutionStatus.NOT_APPLICABLE, null, 0.9, List.of(evidence), List.of(), "node-a:PREDICATE");
        BusinessRuleCandidate second = factory.create("predicate-1", "state.rule",
            BusinessRuleCategory.STATE_PRECONDITION, ExtractionStatus.EXTRACTED, SemanticStatus.RESOLVED,
            TargetResolutionStatus.NOT_APPLICABLE, null, 0.9, List.of(evidence), List.of(), "node-b:PREDICATE");

        assertThat(first.candidateId()).isNotEqualTo(second.candidateId());
    }
}
