package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class BaselinePreflightSecurityTest {
    @TempDir Path temporary;

    @Test void confinesReadsAndMaterializesTheVerifiedDigest() throws Exception {
        Path allowed = temporary.resolve("allowed"), source = allowed.resolve("corpus/src/main/java/x/A.java");
        Files.createDirectories(source.getParent()); Files.writeString(source, "package x; class A {}");
        var view = new CorpusAccessGuard().confine(allowed, "corpus");
        String digest = new CorpusIntegrityVerifier().digest(view);
        var prepared = new EvaluationWorkspaceMaterializer().materialize(view,
            temporary.resolve("isolated"), "c", java.util.List.of("src/main/java"), digest);
        assertThat(prepared.root().normalize().toString()).contains("isolated");
        assertThat(prepared.sourceDigest()).isEqualTo(digest);
        assertThatThrownBy(() -> new CorpusAccessGuard().confine(allowed, "../outside"))
            .isInstanceOf(CorpusAccessGuard.CorpusAccessException.class);
    }

    @Test void invalidRequiredDigestStopsBeforeAnalyzerInvocation() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        String json = Files.readString(workspace.manifest()).replaceFirst("b1f5f036", "01f5f036");
        Files.writeString(workspace.manifest(), json);
        AtomicInteger calls = new AtomicInteger();
        SingleRunCoordinator coordinator = new SingleRunCoordinator(new BaselineJsonCodec(),
            new BaselineManifestValidator(), new CorpusAccessGuard(), new CorpusIntegrityVerifier(),
            new EvaluationWorkspaceMaterializer(), (id, prepared, deadline, metrics) -> {
                calls.incrementAndGet(); throw new AssertionError("must not be invoked");
            }, new CanonicalSnapshotBuilder(), new BaselineDiffer(), new CoverageCalculator());
        BaselineRunResult result = coordinator.run(BaselineTestWorkspace.proposal(workspace));
        assertThat(result.status()).isEqualTo(BaselineRunResult.RunStatus.FAILED_PREFLIGHT);
        assertThat(result.analyzerInvocations()).isZero(); assertThat(calls).hasValue(0);
        assertThat(result.diagnostics()).extracting(BaselineDiagnostic::code)
            .contains("CORPUS_INTEGRITY_MISMATCH");
    }
}
