package io.atworks.apiintelligence.config;

public record ConfigurationDiagnostic(String code, String message) {

    public ConfigurationDiagnostic {
        if (code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("configuration diagnostic is blank");
        }
    }
}
