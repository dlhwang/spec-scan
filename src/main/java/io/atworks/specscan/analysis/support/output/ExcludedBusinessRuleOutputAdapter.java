package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

final class ExcludedBusinessRuleOutputAdapter {
    private static final Set<String> EXCLUDED_RULE_ALLOWLIST = Set.of(
        "SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW", "JDK_OPTIONAL_LOOKUP_FAILURE",
        "SPRING_SECURITY_PASSWORD_MATCH_FAILURE", "JAVA_AUTHORIZATION_GUARD_CALL");

    void add(BusinessRuleCandidate candidate, NormalizedConstraint constraint,
             List<ExcludedBusinessRule> excluded, List<CandidateOutputDiagnostic> diagnostics) {
        if (constraint == null) {
            addExcluded(candidate, null, "CONTROL_FLOW_ONLY", excluded, diagnostics);
        } else if (constraint.kind() == ConstraintKind.RUNTIME_DEPENDENT) {
            addExcluded(candidate, constraint, "RUNTIME_DEPENDENT", excluded, diagnostics);
        } else if (candidate.targetStatus() != TargetResolutionStatus.RESOLVED) {
            addExcluded(candidate, constraint, "TARGET_UNRESOLVED", excluded, diagnostics);
        } else if (constraint.kind() == ConstraintKind.CONTROL_FLOW_ONLY
                || constraint.kind() == ConstraintKind.INPUT_TO_DOMAIN) {
            addExcluded(candidate, constraint, "EXTERNAL_STATE_REQUIRED", excluded, diagnostics);
        } else {
            diagnostics.add(diagnostic("BUSINESS_RESTRICTION_NOT_EXCLUDED",
                "Business restriction did not match an excluded-rule reason", candidate));
        }
    }

    private void addExcluded(BusinessRuleCandidate candidate, NormalizedConstraint constraint, String reason,
                             List<ExcludedBusinessRule> excluded,
                             List<CandidateOutputDiagnostic> diagnostics) {
        if (!EXCLUDED_RULE_ALLOWLIST.contains(candidate.ruleId())) {
            diagnostics.add(diagnostic("EXCLUDED_RULE_NOT_ALLOWLISTED",
                "Rule has no registered excluded-business-rule template", candidate));
            return;
        }
        boolean predicate = candidate.evidence().stream().anyMatch(ref -> ref.role() == EvidenceRole.PREDICATE);
        boolean outcome = candidate.evidence().stream().anyMatch(ref -> ref.role() == EvidenceRole.FAILURE_OUTCOME);
        if (!predicate || !outcome) {
            diagnostics.add(diagnostic("EXCLUDED_RULE_EVIDENCE_INCOMPLETE",
                "Excluded rule requires predicate and failure outcome facts", candidate));
            return;
        }
        excluded.add(excluded(candidate, constraint, reason));
    }

    private ExcludedBusinessRule excluded(BusinessRuleCandidate candidate, NormalizedConstraint constraint,
                                          String reason) {
        boolean nonExecutable = constraint != null && (constraint.kind() == ConstraintKind.RUNTIME_DEPENDENT
            || constraint.kind() == ConstraintKind.CONTROL_FLOW_ONLY
            || constraint.kind() == ConstraintKind.INPUT_TO_DOMAIN);
        return new ExcludedBusinessRule(candidate.ruleId(), candidate.category(),
            constraint == null ? ConstraintKind.CONTROL_FLOW_ONLY : constraint.kind(), candidate.extractionStatus(),
            candidate.semanticStatus(), candidate.targetStatus(), reason,
            constraint == null ? null : constraint.targetPath(),
            constraint == null || nonExecutable ? null : constraint.operator(),
            constraint == null || nonExecutable ? List.of() : constraint.expectedValues(),
            constraint == null ? null : constraint.expectedSource(), candidate.confidence(), candidate.evidence());
    }

    private CandidateOutputDiagnostic diagnostic(String code, String message, BusinessRuleCandidate candidate) {
        return new CandidateOutputDiagnostic(code, message, candidate.candidateId(), candidate.ruleId());
    }
}
