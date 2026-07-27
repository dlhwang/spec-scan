package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.nio.charset.StandardCharsets;

public final class BaselineArtifactRenderer {
    private final BaselineJsonCodec codec;
    public BaselineArtifactRenderer() { this(new BaselineJsonCodec()); }
    public BaselineArtifactRenderer(BaselineJsonCodec codec) { this.codec = codec; }

    public RenderedArtifacts render(EvaluationResultBundle bundle) {
        byte[] json = codec.toCanonicalJson(bundle);
        int corpusCount = bundle.runResult().snapshot() == null ? 0
            : bundle.runResult().snapshot().corpusRuns().size();
        int changeCount = bundle.runResult().diff() == null ? 0 : bundle.runResult().diff().changes().size();
        String markdown = "# U01 Generalization Baseline\n\n"
            + "- Bundle: `" + bundle.bundleId() + "`\n"
            + "- Status: **" + bundle.overallStatus() + "**\n"
            + "- Corpora: " + corpusCount + "\n"
            + "- Baseline changes/proposals: " + changeCount + "\n"
            + "- Diagnostics: " + bundle.diagnostics().size() + "\n"
            + "- Deterministic: " + (bundle.determinism() == null ? "not evaluated"
                : bundle.determinism().deterministic()) + "\n";
        if (!markdown.contains(bundle.bundleId()) || !markdown.contains(bundle.overallStatus().name()))
            throw new IllegalStateException("REPORT_CONSISTENCY_MISMATCH");
        return new RenderedArtifacts(json, markdown.getBytes(StandardCharsets.UTF_8));
    }

    public record RenderedArtifacts(byte[] json, byte[] markdown) {
        public RenderedArtifacts { json = json.clone(); markdown = markdown.clone(); }
        @Override public byte[] json() { return json.clone(); }
        @Override public byte[] markdown() { return markdown.clone(); }
    }
}
