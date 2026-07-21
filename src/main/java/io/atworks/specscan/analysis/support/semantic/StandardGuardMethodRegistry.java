package io.atworks.specscan.analysis.support.semantic;

import java.util.List;
import java.util.Optional;

public final class StandardGuardMethodRegistry {
    private final List<StandardGuardMethodDescriptor> descriptors;

    public StandardGuardMethodRegistry(List<StandardGuardMethodDescriptor> descriptors) {
        this.descriptors = List.copyOf(descriptors);
    }

    public static StandardGuardMethodRegistry defaults() {
        return new StandardGuardMethodRegistry(List.of(
            throwsOnInvalid("java.util.Objects.requireNonNull", "NOT_NULL"),
            throwsOnInvalid("com.google.common.base.Preconditions.checkNotNull", "NOT_NULL"),
            throwsOnInvalid("org.springframework.util.Assert.notNull", "NOT_NULL"),
            returnsInvalid("org.springframework.util.ObjectUtils.isEmpty", "NOT_EMPTY"),
            returnsInvalid("org.springframework.util.StringUtils.isEmpty", "NOT_EMPTY")
        ));
    }

    public Optional<StandardGuardMethodDescriptor> find(String resolvedSignature) {
        return descriptors.stream().filter(descriptor -> descriptor.matches(resolvedSignature)).findFirst();
    }

    private static StandardGuardMethodDescriptor throwsOnInvalid(String prefix, String operator) {
        return new StandardGuardMethodDescriptor(prefix, 0, GuardFailureSemantics.THROWS_ON_INVALID, operator);
    }

    private static StandardGuardMethodDescriptor returnsInvalid(String prefix, String operator) {
        return new StandardGuardMethodDescriptor(prefix, 0,
            GuardFailureSemantics.RETURNS_TRUE_WHEN_INVALID, operator);
    }
}
