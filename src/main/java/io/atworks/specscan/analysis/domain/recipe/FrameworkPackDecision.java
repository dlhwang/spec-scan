package io.atworks.specscan.analysis.domain.recipe;

public record FrameworkPackDecision(String packId, boolean activated, String status, String diagnostic) {
    public static FrameworkPackDecision active(String id) { return new FrameworkPackDecision(id, true, "ACTIVATED", ""); }
    public static FrameworkPackDecision unresolved(String id, String diagnostic) { return new FrameworkPackDecision(id, false, "UNRESOLVED", diagnostic); }
}
