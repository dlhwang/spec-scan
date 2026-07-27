package io.atworks.specscan.analysis.domain.evaluation;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record BaselineRunRequest(RunPurpose purpose, Path corpusManifestPath, Path baselineManifestPath,
                                 EvaluationProfile profile, String engineRevision, Path allowedCorpusRoot,
                                 Path evaluationWorkspaceRoot, Path outputRoot,
                                 Map<String, LocalCorpusBinding> localCorpusBindings) {
    public enum RunPurpose { SNAPSHOT_PROPOSAL, G01_DIFF, G01_DETERMINISM }

    public BaselineRunRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(corpusManifestPath, "corpusManifestPath");
        Objects.requireNonNull(profile, "profile");
        requireText(engineRevision, "engineRevision");
        Objects.requireNonNull(allowedCorpusRoot, "allowedCorpusRoot");
        Objects.requireNonNull(evaluationWorkspaceRoot, "evaluationWorkspaceRoot");
        Objects.requireNonNull(outputRoot, "outputRoot");
        localCorpusBindings = Map.copyOf(Objects.requireNonNull(localCorpusBindings, "localCorpusBindings"));
        if (purpose != RunPurpose.SNAPSHOT_PROPOSAL && baselineManifestPath == null)
            throw new IllegalArgumentException("approved baseline is required for " + purpose);
    }

    public BaselineRunRequest(RunPurpose purpose, Path corpusManifestPath, Path baselineManifestPath,
                              EvaluationProfile profile, String engineRevision, Path allowedCorpusRoot,
                              Path evaluationWorkspaceRoot, Path outputRoot) {
        this(purpose, corpusManifestPath, baselineManifestPath, profile, engineRevision, allowedCorpusRoot,
            evaluationWorkspaceRoot, outputRoot, Map.of());
    }

    public record LocalCorpusBinding(Path allowedRoot, String locator, String expectedDigest, String commitRef) {
        public LocalCorpusBinding {
            Objects.requireNonNull(allowedRoot, "allowedRoot");
            requireText(locator, "locator");
            if (expectedDigest == null || !expectedDigest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("expectedDigest must be lowercase SHA-256");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
