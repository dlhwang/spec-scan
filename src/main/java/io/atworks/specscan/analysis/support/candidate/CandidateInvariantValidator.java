package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;

public final class CandidateInvariantValidator {
    public void validate(PredicateCandidate candidate) {
        if (candidate.extractionStatus() != ExtractionStatus.EXTRACTED && candidate.diagnostics().isEmpty()) {
            throw invalid("non-extracted predicate requires diagnostic");
        }
    }

    public void validate(BusinessRuleCandidate candidate) {
        boolean resolved = candidate.semanticStatus() == SemanticStatus.RESOLVED;
        if (resolved && (candidate.ruleId() == null || candidate.ruleId().isBlank())) {
            throw invalid("resolved semantic requires ruleId");
        }
        if (resolved && candidate.category() == BusinessRuleCategory.UNKNOWN) {
            throw invalid("resolved semantic cannot use UNKNOWN category");
        }
        if (!resolved && candidate.category() != BusinessRuleCategory.UNKNOWN) {
            throw invalid("unresolved semantic must use UNKNOWN category");
        }
        String targetPath = candidate.constraint() == null ? null : candidate.constraint().targetPath();
        if (candidate.targetStatus() == TargetResolutionStatus.RESOLVED
            && (targetPath == null || targetPath.isBlank())) {
            throw invalid("resolved target requires targetPath");
        }
        if (candidate.targetStatus() != TargetResolutionStatus.RESOLVED && targetPath != null) {
            throw invalid("non-resolved target cannot have targetPath");
        }
        if (requiresDiagnostic(candidate) && candidate.diagnostics().isEmpty()) {
            throw invalid("partial or unresolved candidate requires diagnostic");
        }
    }

    private boolean requiresDiagnostic(BusinessRuleCandidate candidate) {
        return candidate.extractionStatus() != ExtractionStatus.EXTRACTED
            || candidate.semanticStatus() != SemanticStatus.RESOLVED
            || candidate.targetStatus() == TargetResolutionStatus.UNRESOLVED;
    }
    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("INVALID_STATUS_COMBINATION: " + message);
    }
}
