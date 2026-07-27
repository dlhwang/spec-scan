package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.CompletionManifest;
import java.io.IOException;
import java.nio.file.*;

public final class CompletedBundleReader {
    private final BaselineJsonCodec codec;
    private final SchemaVersionReaderRegistry readers;
    public CompletedBundleReader() {
        codec = new BaselineJsonCodec(); readers = SchemaVersionReaderRegistry.v1(codec);
    }

    public CompletionManifest read(Path bundleRoot) {
        Path completionPath = bundleRoot.resolve("completion-manifest.json");
        if (!Files.isRegularFile(completionPath))
            throw new IllegalArgumentException("INCOMPLETE_BUNDLE: " + bundleRoot);
        CompletionManifest completion = readers.read(SchemaVersionReaderRegistry.ArtifactKind.COMPLETION_MANIFEST,
            codec.readSchemaVersion(completionPath), completionPath, CompletionManifest.class);
        for (CompletionManifest.PayloadDigest payload : completion.payloads()) {
            Path path = bundleRoot.resolve(payload.fileName()).normalize();
            if (!path.startsWith(bundleRoot) || !Files.isRegularFile(path))
                throw new IllegalArgumentException("CORRUPT_BUNDLE: missing " + payload.fileName());
            try {
                if (!CorpusIntegrityVerifier.sha256(Files.readAllBytes(path)).equals(payload.sha256()))
                    throw new IllegalArgumentException("CORRUPT_BUNDLE: digest " + payload.fileName());
            } catch (IOException exception) {
                throw new IllegalArgumentException("CORRUPT_BUNDLE: " + payload.fileName(), exception);
            }
        }
        return completion;
    }
}
