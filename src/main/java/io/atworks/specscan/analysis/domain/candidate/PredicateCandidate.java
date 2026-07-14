package io.atworks.specscan.analysis.domain.candidate;

import java.util.List;
import java.util.Objects;

public record PredicateCandidate(String candidateId, String graphId, String conditionNodeId,
                                 PredicateType predicateType, ExtractionStatus extractionStatus,
                                 List<EvidenceRef> evidence, List<CandidateDiagnostic> diagnostics) {
    public PredicateCandidate {
        requireText(candidateId, "candidateId");
        requireText(graphId, "graphId");
        requireText(conditionNodeId, "conditionNodeId");
        Objects.requireNonNull(predicateType, "predicateType");
        Objects.requireNonNull(extractionStatus, "extractionStatus");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (evidence.isEmpty()) throw new IllegalArgumentException("candidate evidence is required");
        if (extractionStatus != ExtractionStatus.EXTRACTED && diagnostics.isEmpty()) {
            throw new IllegalArgumentException("non-extracted candidate diagnostic is required");
        }
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
