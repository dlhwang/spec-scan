package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.fact.TypeResolution;
import io.atworks.specscan.analysis.domain.fact.TypeResolutionStatus;

public final class SpringSecurityPasswordMatcher {
    private static final String NAMESPACE = "org.springframework.security.crypto.";
    private static final String SIGNATURE_SUFFIX =
        ".matches(java.lang.CharSequence, java.lang.String)";

    private SpringSecurityPasswordMatcher() {}

    public static boolean matches(TypeResolution resolution) {
        if (resolution == null || resolution.status() != TypeResolutionStatus.RESOLVED) return false;
        String signature = resolution.resolvedSignature();
        return signature != null
            && signature.startsWith(NAMESPACE)
            && signature.endsWith(SIGNATURE_SUFFIX);
    }

    public static boolean matches(FactGraphIndex index, io.atworks.specscan.analysis.domain.fact.FactNode call) {
        if (matches(call.typeResolution())) return true;
        if (!(call.payload() instanceof io.atworks.specscan.analysis.domain.fact.FactNodePayload.MethodCallPayload payload)
                || !"matches".equals(payload.methodName())) return false;
        return index.targets(call.id(), io.atworks.specscan.analysis.domain.fact.FactEdgeType.OPERAND_OF, "RECEIVER")
            .stream().anyMatch(receiver -> isPasswordEncoderType(receiver.typeResolution())
                || index.targets(receiver.id(), io.atworks.specscan.analysis.domain.fact.FactEdgeType.READS)
                    .stream().anyMatch(declaration -> isPasswordEncoderType(declaration.typeResolution())));
    }

    private static boolean isPasswordEncoderType(TypeResolution resolution) {
        String type = resolution == null ? null : resolution.qualifiedType();
        return resolution != null && resolution.status() == TypeResolutionStatus.RESOLVED
            && type != null && type.startsWith(NAMESPACE) && type.endsWith("PasswordEncoder");
    }
}
