package io.atworks.specscan.analysis.support.semantic;

import java.util.Objects;

public record StandardGuardMethodDescriptor(String qualifiedMethodPrefix, int argumentIndex,
                                            GuardFailureSemantics failureSemantics,
                                            String normalizedOperator) {
    public StandardGuardMethodDescriptor {
        Objects.requireNonNull(qualifiedMethodPrefix, "qualifiedMethodPrefix");
        Objects.requireNonNull(failureSemantics, "failureSemantics");
        Objects.requireNonNull(normalizedOperator, "normalizedOperator");
        if (qualifiedMethodPrefix.isBlank() || argumentIndex < 0 || normalizedOperator.isBlank()) {
            throw new IllegalArgumentException("invalid guard method descriptor");
        }
    }

    boolean matches(String signature) {
        return signature != null && signature.startsWith(qualifiedMethodPrefix + "(");
    }
}
