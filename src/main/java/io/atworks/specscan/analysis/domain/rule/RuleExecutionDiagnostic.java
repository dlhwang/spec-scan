package io.atworks.specscan.analysis.domain.rule;

import java.util.Objects;

public record RuleExecutionDiagnostic(RuleExecutionDiagnosticSeverity severity, String code, String ruleId,
                                      String predicateCandidateId, String exceptionType, String message) {
    public RuleExecutionDiagnostic {
        Objects.requireNonNull(severity, "severity");
        if (code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("diagnostic code and message are required");
        }
    }
}
