package io.atworks.specscan.analysis.domain.recipe;

public record RecipeDiagnostic(String file, String path, String code, String message) {
    public RecipeDiagnostic {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
    }
}
