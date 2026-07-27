package io.atworks.specscan.analysis.domain.recipe;

public record FrameworkEvidence(String ownerType, String method, String resolvedType, boolean graphPathProven) {
    public FrameworkEvidence {
        if (ownerType == null || ownerType.isBlank() || method == null || method.isBlank())
            throw new IllegalArgumentException("ownerType and method are required");
    }
}
