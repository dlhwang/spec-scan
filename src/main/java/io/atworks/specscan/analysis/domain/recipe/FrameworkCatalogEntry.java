package io.atworks.specscan.analysis.domain.recipe;

public record FrameworkCatalogEntry(String id, String ownerType, String method,
                                    String argumentRole, String semantics) {
    public FrameworkCatalogEntry {
        require(id, "id"); require(ownerType, "ownerType"); require(method, "method");
        require(argumentRole, "argumentRole"); require(semantics, "semantics");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
