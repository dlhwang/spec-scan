package io.atworks.specscan.analysis.domain.recipe;

public record RecipePackVersion(int schemaVersion, String semanticPackVersion, String frameworkPackVersion) {
    public RecipePackVersion {
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        require(semanticPackVersion, "semanticPackVersion");
        require(frameworkPackVersion, "frameworkPackVersion");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
