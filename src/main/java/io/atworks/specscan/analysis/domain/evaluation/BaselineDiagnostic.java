package io.atworks.specscan.analysis.domain.evaluation;

import java.util.Comparator;
import java.util.Objects;

public record BaselineDiagnostic(String code, Severity severity, String stage, String corpusId,
                                 String operationKey, String identity, String details) {
    public enum Severity { INFO, WARNING, ERROR }

    public BaselineDiagnostic {
        requireText(code, "code");
        Objects.requireNonNull(severity, "severity");
        requireText(stage, "stage");
        requireText(details, "details");
    }

    public static Comparator<BaselineDiagnostic> canonicalOrder() {
        return Comparator.comparing(BaselineDiagnostic::corpusId, Comparator.nullsFirst(String::compareTo))
            .thenComparing(BaselineDiagnostic::operationKey, Comparator.nullsFirst(String::compareTo))
            .thenComparing(BaselineDiagnostic::identity, Comparator.nullsFirst(String::compareTo))
            .thenComparing(BaselineDiagnostic::stage)
            .thenComparing(BaselineDiagnostic::code);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
