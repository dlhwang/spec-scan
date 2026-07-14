package io.atworks.specscan.analysis.domain.output;

public enum MigrationDifferenceKind {
    LEGACY_ONLY, NEW_ONLY, EQUIVALENT, CONFLICTING, UNRESOLVED_BY_NEW_ENGINE,
    LEGACY_SCOPE_UNRESOLVED, NO_CONDITIONS_OBSERVED
}
