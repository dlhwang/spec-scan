package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.BaselineDiff;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class ArtifactMigrationRegistry {
    public record MigrationKey(SchemaVersionReaderRegistry.ArtifactKind kind, int sourceVersion,
                               int targetVersion) {
        public MigrationKey {
            Objects.requireNonNull(kind, "kind");
            if (sourceVersion <= 0 || targetVersion <= 0 || sourceVersion == targetVersion)
                throw new IllegalArgumentException("distinct positive versions are required");
        }
    }
    public record MigrationResult(Path source, Path target, BaselineDiff.Result semanticDiff) {}
    @FunctionalInterface public interface ArtifactMigrator {
        MigrationResult migrate(Path source, Path target);
    }

    private final Map<MigrationKey, ArtifactMigrator> migrators;
    public ArtifactMigrationRegistry(Map<MigrationKey, ArtifactMigrator> migrators) {
        this.migrators = Map.copyOf(Objects.requireNonNull(migrators, "migrators"));
    }
    public static ArtifactMigrationRegistry empty() { return new ArtifactMigrationRegistry(Map.of()); }

    public MigrationResult migrate(MigrationKey key, Path source, Path target) {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(target, "target");
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("migration cannot overwrite source");
        ArtifactMigrator migrator = migrators.get(key);
        if (migrator == null) throw new IllegalArgumentException("MIGRATION_PATH_NOT_FOUND: " + key);
        return migrator.migrate(source, target);
    }
}
