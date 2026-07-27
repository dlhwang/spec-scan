package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

public final class ArtifactBundlePublisher {
    private final BaselineJsonCodec codec;
    public ArtifactBundlePublisher() { this(new BaselineJsonCodec()); }
    public ArtifactBundlePublisher(BaselineJsonCodec codec) { this.codec = codec; }

    public Path publish(Path outputRoot, EvaluationResultBundle bundle,
                        BaselineArtifactRenderer.RenderedArtifacts rendered) {
        Path bundleRoot = outputRoot.resolve(bundle.bundleId());
        try {
            Files.createDirectories(outputRoot);
            Files.createDirectory(bundleRoot);
            Path json = bundleRoot.resolve("evaluation-result.json");
            Path markdown = bundleRoot.resolve("summary.md");
            Files.write(json, rendered.json(), StandardOpenOption.CREATE_NEW);
            Files.write(markdown, rendered.markdown(), StandardOpenOption.CREATE_NEW);
            List<CompletionManifest.PayloadDigest> payloads = List.of(
                new CompletionManifest.PayloadDigest(json.getFileName().toString(),
                    CorpusIntegrityVerifier.sha256(Files.readAllBytes(json))),
                new CompletionManifest.PayloadDigest(markdown.getFileName().toString(),
                    CorpusIntegrityVerifier.sha256(Files.readAllBytes(markdown))));
            CompletionManifest completion = new CompletionManifest(1, bundle.bundleId(), bundle.inputIdentity(),
                bundle.overallStatus(), payloads, Instant.now().toString(), CompletionManifest.MARKER);
            Files.write(bundleRoot.resolve("completion-manifest.json"), codec.toCanonicalJson(completion),
                StandardOpenOption.CREATE_NEW);
            return bundleRoot;
        } catch (IOException exception) {
            throw new IllegalStateException("ARTIFACT_PUBLICATION_FAILED: " + bundleRoot, exception);
        }
    }
}
