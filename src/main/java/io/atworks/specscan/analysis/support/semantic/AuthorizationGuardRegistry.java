package io.atworks.specscan.analysis.support.semantic;

import java.util.List;
import java.util.Optional;

public final class AuthorizationGuardRegistry {
    private final List<AuthorizationGuardDescriptor> descriptors;

    public AuthorizationGuardRegistry(List<AuthorizationGuardDescriptor> descriptors) {
        this.descriptors = List.copyOf(descriptors);
    }

    public static AuthorizationGuardRegistry defaults() {
        return new AuthorizationGuardRegistry(List.of());
    }

    public Optional<AuthorizationGuardDescriptor> find(String resolvedSignature) {
        return descriptors.stream().filter(descriptor -> descriptor.matches(resolvedSignature)).findFirst();
    }
}
