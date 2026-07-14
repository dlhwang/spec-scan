package io.atworks.specscan.analysis.domain.candidate;

import java.util.List;
import java.util.Objects;

public record BusinessRuleCandidate(String candidateId, String predicateCandidateId, String ruleId,
                                    BusinessRuleCategory category, ExtractionStatus extractionStatus,
                                    SemanticStatus semanticStatus, TargetResolutionStatus targetStatus,
                                    NormalizedConstraint constraint, double confidence,
                                    List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics) {
    public BusinessRuleCandidate {
        requireText(candidateId, "candidateId");
        requireText(predicateCandidateId, "predicateCandidateId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(extractionStatus, "extractionStatus");
        Objects.requireNonNull(semanticStatus, "semanticStatus");
        Objects.requireNonNull(targetStatus, "targetStatus");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (evidence.isEmpty()) throw new IllegalArgumentException("candidate evidence is required");
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
