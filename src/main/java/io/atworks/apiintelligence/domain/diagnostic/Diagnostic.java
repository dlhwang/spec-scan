package io.atworks.apiintelligence.domain.diagnostic;

public record Diagnostic(String code, String stage, String message, boolean retryable) {

    public Diagnostic {
        code = req(code);
        stage = req(stage);
        message = req(message);
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("diagnostic value is blank");
        }
        return v.trim();
    }
}
