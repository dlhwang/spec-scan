package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import java.util.*;

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
        return create(predicateCandidateId, ruleId, category, RuleEffect.BUSINESS_RESTRICTION,
            extractionStatus, semanticStatus, targetStatus, constraint, confidence, evidence, diagnostics);
    }

    public BusinessRuleCandidate create(String predicateCandidateId, String ruleId,
            BusinessRuleCategory category, RuleEffect effect, ExtractionStatus extractionStatus,
            SemanticStatus semanticStatus, TargetResolutionStatus targetStatus, NormalizedConstraint constraint,
            double confidence, List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics) {
        evidence = distinctEvidence(evidence);
        BusinessRuleCandidate candidate = new BusinessRuleCandidate(
            ids.forBusinessRule(predicateCandidateId, ruleId, semanticStatus), predicateCandidateId, ruleId,
            category, effect, extractionStatus, semanticStatus, targetStatus, constraint, confidence, evidence, diagnostics);
        validator.validate(candidate);
        return candidate;
    }

    public BusinessRuleCandidate create(String predicateCandidateId, String ruleId,
            BusinessRuleCategory category, ExtractionStatus extractionStatus, SemanticStatus semanticStatus,
            TargetResolutionStatus targetStatus, NormalizedConstraint constraint, double confidence,
            List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics, String evidenceFingerprint) {
        return create(predicateCandidateId, ruleId, category, RuleEffect.BUSINESS_RESTRICTION,
            extractionStatus, semanticStatus, targetStatus, constraint, confidence, evidence, diagnostics,
            evidenceFingerprint);
    }

    public BusinessRuleCandidate create(String predicateCandidateId, String ruleId,
            BusinessRuleCategory category, RuleEffect effect, ExtractionStatus extractionStatus,
            SemanticStatus semanticStatus, TargetResolutionStatus targetStatus, NormalizedConstraint constraint,
            double confidence, List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics,
            String evidenceFingerprint) {
        evidence = distinctEvidence(evidence);
        BusinessRuleCandidate candidate = new BusinessRuleCandidate(
            ids.forBusinessRule(predicateCandidateId, ruleId, semanticStatus, evidenceFingerprint),
            predicateCandidateId, ruleId, category, effect, extractionStatus, semanticStatus, targetStatus,
            constraint, confidence, evidence, diagnostics);
        validator.validate(candidate);
        return candidate;
    }

    private List<EvidenceRef> distinctEvidence(List<EvidenceRef> evidence) {
        Map<String, EvidenceRef> result = new LinkedHashMap<>();
        for (EvidenceRef value : evidence) {
            String key = value.filePath() + "|" + value.startLine() + "|" + value.startColumn()
                + "|" + value.endLine() + "|" + value.endColumn() + "|" + value.role()
                + "|" + value.snippet();
            result.putIfAbsent(key, value);
        }
        return List.copyOf(result.values());
    }
}
