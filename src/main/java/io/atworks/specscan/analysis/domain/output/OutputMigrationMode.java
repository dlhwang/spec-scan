package io.atworks.specscan.analysis.domain.output;

public enum OutputMigrationMode {
    LEGACY_ONLY, COMPARE, NEW_ONLY;

    public static OutputMigrationMode configured(String value) {
        if (value == null || value.isBlank()) return COMPARE;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return COMPARE;
        }
    }
}
