package io.atworks.specscan.analysis.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.support.candidate.CandidateInvariantValidator;
import java.util.List;
import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.*;

class CandidateInvariantProperties {
    private final CandidateInvariantValidator validator = new CandidateInvariantValidator();
    private final EvidenceRef evidence = new EvidenceRef("node", "src/Test.java", 1, 1, 1, 2,
        EvidenceRole.PREDICATE, "x");

    @Property(tries = 200, generation = GenerationMode.RANDOMIZED)
    void resolvedCandidatesAcceptBusinessCategories(@ForAll("resolvedCategories") BusinessRuleCategory category) {
        BusinessRuleCandidate candidate = new BusinessRuleCandidate("candidate", "predicate", "rule", category,
            ExtractionStatus.EXTRACTED, SemanticStatus.RESOLVED, TargetResolutionStatus.NOT_APPLICABLE,
            null, 1.0, List.of(evidence), List.of());
        assertThatCode(() -> validator.validate(candidate)).doesNotThrowAnyException();
    }

    @Property(tries = 200, generation = GenerationMode.RANDOMIZED)
    void unresolvedCandidatesRejectResolvedCategories(@ForAll("resolvedCategories") BusinessRuleCategory category) {
        BusinessRuleCandidate candidate = new BusinessRuleCandidate("candidate", "predicate", null, category,
            ExtractionStatus.EXTRACTED, SemanticStatus.UNRESOLVED, TargetResolutionStatus.UNRESOLVED,
            null, 0.0, List.of(evidence), List.of(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
                "RULE_UNRESOLVED", "unresolved", "node")));
        assertThatThrownBy(() -> validator.validate(candidate)).hasMessageContaining("INVALID_STATUS_COMBINATION");
    }

    @Provide
    Arbitrary<BusinessRuleCategory> resolvedCategories() {
        return Arbitraries.of(BusinessRuleCategory.values()).filter(category -> category != BusinessRuleCategory.UNKNOWN);
    }
}
