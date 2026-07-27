package io.atworks.specscan.analysis.support.evaluation;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;

public final class EvaluationWorkspaceMaterializer {
    public record PreparedEvaluationWorkspace(Path root, String sourceDigest, List<String> sourceRoots) {
        public PreparedEvaluationWorkspace {
            Objects.requireNonNull(root, "root"); Objects.requireNonNull(sourceDigest, "sourceDigest");
            sourceRoots = List.copyOf(sourceRoots);
        }
    }

    public PreparedEvaluationWorkspace materialize(CorpusAccessGuard.ConfinedCorpusView view,
                                                   Path callerOwnedRoot, String corpusId,
                                                   List<String> sourceRoots, String expectedDigest) {
        Path target = callerOwnedRoot.resolve(corpusId);
        try {
            Files.createDirectories(callerOwnedRoot);
            Files.createDirectory(target);
            for (String relative : view.listSourcePaths()) {
                Path output = target.resolve(relative).normalize();
                if (!output.startsWith(target)) throw new IllegalArgumentException("materialized path escaped root");
                Files.createDirectories(output.getParent());
                Files.write(output, view.readSource(relative), StandardOpenOption.CREATE_NEW);
            }
            CorpusAccessGuard.ConfinedCorpusView prepared = new CorpusAccessGuard()
                .confine(callerOwnedRoot, corpusId);
            String digest = new CorpusIntegrityVerifier().digest(prepared);
            if (!digest.equals(expectedDigest))
                throw new IllegalStateException("MATERIALIZED_DIGEST_MISMATCH: " + corpusId);
            return new PreparedEvaluationWorkspace(target.toAbsolutePath().normalize(), digest, sourceRoots);
        } catch (IOException exception) {
            throw new IllegalStateException("evaluation workspace materialization failed: " + corpusId, exception);
        }
    }
}
