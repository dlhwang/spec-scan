package io.atworks.specscan.analysis.domain.output;

public record MigrationDifference(MigrationDifferenceKind kind, String key, String details) {}
