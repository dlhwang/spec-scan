package io.atworks.apiintelligence.config;

public final class ConfigurationException extends RuntimeException {

    private final ConfigurationDiagnostic diagnostic;

    public ConfigurationException(String code, String message) {
        super(message);
        this.diagnostic = new ConfigurationDiagnostic(code, message);
    }

    public ConfigurationDiagnostic diagnostic() {
        return diagnostic;
    }
}
