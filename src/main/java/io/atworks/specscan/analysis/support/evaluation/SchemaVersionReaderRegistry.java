package io.atworks.specscan.analysis.support.evaluation;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class SchemaVersionReaderRegistry {
    public enum ArtifactKind { CORPUS_MANIFEST, BASELINE_MANIFEST, COMPLETION_MANIFEST }
    @FunctionalInterface public interface ArtifactReader<T> { T read(Path path); }
    private record Key(ArtifactKind kind, int version) {}

    private final Map<Key, ArtifactReader<?>> readers;

    public SchemaVersionReaderRegistry(Map<ReaderKey, ArtifactReader<?>> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        this.readers = registrations.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            entry -> new Key(entry.getKey().kind(), entry.getKey().version()), Map.Entry::getValue));
    }

    public static SchemaVersionReaderRegistry v1(BaselineJsonCodec codec) {
        return new SchemaVersionReaderRegistry(Map.of(
            new ReaderKey(ArtifactKind.CORPUS_MANIFEST, 1), codec::readCorpusManifest,
            new ReaderKey(ArtifactKind.BASELINE_MANIFEST, 1), codec::readBaselineManifest,
            new ReaderKey(ArtifactKind.COMPLETION_MANIFEST, 1), codec::readCompletionManifest));
    }

    @SuppressWarnings("unchecked")
    public <T> T read(ArtifactKind kind, int version, Path path, Class<T> expectedType) {
        ArtifactReader<?> reader = readers.get(new Key(kind, version));
        if (reader == null) throw new IllegalArgumentException("UNSUPPORTED_SCHEMA_VERSION: " + kind + " v" + version);
        Object value = reader.read(path);
        if (!expectedType.isInstance(value)) throw new IllegalStateException("reader type mismatch for " + kind);
        return (T) value;
    }

    public record ReaderKey(ArtifactKind kind, int version) {
        public ReaderKey {
            Objects.requireNonNull(kind, "kind");
            if (version <= 0) throw new IllegalArgumentException("version must be positive");
        }
    }
}
