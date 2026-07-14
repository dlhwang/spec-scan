package io.atworks.specscan.analysis.support.fact;

import io.atworks.specscan.analysis.domain.fact.TraversalDecision;

public final class DefaultMethodTraversalPolicy {
    public TraversalDecision decide(String qualifiedOwner, String methodName, boolean sourceAvailable, String applicationBasePackage) {
        if (qualifiedOwner == null || methodName == null) return TraversalDecision.RECORD_CALL_ONLY;
        String lower = qualifiedOwner.toLowerCase();
        if (lower.contains("generated") || lower.contains("$$") || lower.contains("proxy")) return TraversalDecision.SKIP_GENERATED;
        if (!sourceAvailable || qualifiedOwner.startsWith("java.") || qualifiedOwner.startsWith("javax.") || qualifiedOwner.startsWith("jakarta.") || qualifiedOwner.startsWith("org.springframework.")) return TraversalDecision.RECORD_CALL_ONLY;
        if (lower.contains("repository") || accessor(methodName)) return TraversalDecision.RECORD_CALL_ONLY;
        return applicationBasePackage == null || applicationBasePackage.isBlank() || qualifiedOwner.startsWith(applicationBasePackage)
            ? TraversalDecision.VISIT_BODY : TraversalDecision.RECORD_CALL_ONLY;
    }
    private boolean accessor(String name) { return (name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) && name.length() > 2; }
}
