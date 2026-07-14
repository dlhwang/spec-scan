package io.atworks.specscan.analysis.domain.candidate;

import java.util.Objects;

public record CandidateDiagnostic(CandidateDiagnosticSeverity severity, String code, String message, String nodeId) {
    public CandidateDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (code.isBlank() || message.isBlank()) throw new IllegalArgumentException("invalid candidate diagnostic");
    }
}
