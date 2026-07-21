package io.atworks.specscan.analysis.support.semantic;

import java.util.Objects;

public record AuthorizationGuardDescriptor(String qualifiedMethodPrefix, boolean failureResult,
                                           String targetPath, String operator) {
    public AuthorizationGuardDescriptor {
        Objects.requireNonNull(qualifiedMethodPrefix, "qualifiedMethodPrefix");
        Objects.requireNonNull(targetPath, "targetPath");
        Objects.requireNonNull(operator, "operator");
        if (qualifiedMethodPrefix.isBlank() || targetPath.isBlank() || operator.isBlank()) {
            throw new IllegalArgumentException("invalid authorization guard descriptor");
        }
    }

    boolean matches(String signature) {
        return signature != null && signature.startsWith(qualifiedMethodPrefix + "(");
    }
}
