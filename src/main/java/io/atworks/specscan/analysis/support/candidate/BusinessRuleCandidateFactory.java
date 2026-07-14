package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import java.util.List;

public final class BusinessRuleCandidateFactory {
    private final DeterministicCandidateIdGenerator ids;
    private final CandidateInvariantValidator validator;

    public BusinessRuleCandidateFactory() {
        this(new DeterministicCandidateIdGenerator(), new CandidateInvariantValidator());
    }
    BusinessRuleCandidateFactory(DeterministicCandidateIdGenerator ids, CandidateInvariantValidator validator) {
        this.ids = ids;
        this.validator = validator;
    }

    public BusinessRuleCandidate create(String predicateCandidateId, String ruleId,
            BusinessRuleCategory category, ExtractionStatus extractionStatus, SemanticStatus semanticStatus,
            TargetResolutionStatus targetStatus, NormalizedConstraint constraint, double confidence,
            List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics) {
        BusinessRuleCandidate candidate = new BusinessRuleCandidate(
            ids.forBusinessRule(predicateCandidateId, ruleId, semanticStatus), predicateCandidateId, ruleId,
            category, extractionStatus, semanticStatus, targetStatus, constraint, confidence, evidence, diagnostics);
        validator.validate(candidate);
        return candidate;
    }

    public BusinessRuleCandidate create(String predicateCandidateId, String ruleId,
            BusinessRuleCategory category, ExtractionStatus extractionStatus, SemanticStatus semanticStatus,
            TargetResolutionStatus targetStatus, NormalizedConstraint constraint, double confidence,
            List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics, String evidenceFingerprint) {
        BusinessRuleCandidate candidate = new BusinessRuleCandidate(
            ids.forBusinessRule(predicateCandidateId, ruleId, semanticStatus, evidenceFingerprint),
            predicateCandidateId, ruleId, category, extractionStatus, semanticStatus, targetStatus,
            constraint, confidence, evidence, diagnostics);
        validator.validate(candidate);
        return candidate;
    }
}
