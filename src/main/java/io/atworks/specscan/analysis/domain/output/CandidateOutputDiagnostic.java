package io.atworks.specscan.analysis.domain.output;

public record CandidateOutputDiagnostic(String code, String message, String candidateId, String ruleId) {
    public CandidateOutputDiagnostic {
        if (code == null || code.isBlank() || message == null || message.isBlank()
                || candidateId == null || candidateId.isBlank())
            throw new IllegalArgumentException("code, message and candidateId are required");
    }
}
