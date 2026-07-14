package io.atworks.specscan.analysis.domain.fact;

import java.util.Objects;

public record TypeResolution(TypeResolutionStatus status, String qualifiedType, String resolvedSignature, String diagnosticCode) {
    public TypeResolution { Objects.requireNonNull(status, "status"); if (status == TypeResolutionStatus.RESOLVED && blank(qualifiedType) && blank(resolvedSignature)) throw new IllegalArgumentException("resolved value required"); }
    public static TypeResolution resolvedType(String type) { return new TypeResolution(TypeResolutionStatus.RESOLVED, type, null, null); }
    public static TypeResolution resolvedSignature(String signature) { return new TypeResolution(TypeResolutionStatus.RESOLVED, null, signature, null); }
    public static TypeResolution unresolved(String code) { return new TypeResolution(TypeResolutionStatus.UNRESOLVED, null, null, code); }
    public static TypeResolution notApplicable() { return new TypeResolution(TypeResolutionStatus.NOT_APPLICABLE, null, null, null); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
