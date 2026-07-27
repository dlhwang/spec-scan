package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class BaselineEvaluationFacade {
    private final SingleRunCoordinator coordinator;
    private final DeterminismVerifier determinismVerifier;
    public BaselineEvaluationFacade() { this(new SingleRunCoordinator()); }
    public BaselineEvaluationFacade(SingleRunCoordinator coordinator) {
        this.coordinator = coordinator; this.determinismVerifier = new DeterminismVerifier(coordinator);
    }

    public EvaluationResultBundle evaluate(BaselineRunRequest request) {
        BaselineRunResult run;
        EvaluationResultBundle.DeterminismResult determinism = null;
        if (request.purpose() == BaselineRunRequest.RunPurpose.G01_DETERMINISM) {
            DeterminismVerifier.Verification verification = determinismVerifier.verify(request);
            run = verification.referenceRun(); determinism = verification.result();
        } else run = coordinator.run(request);
        String inputIdentity = run.snapshot() == null
            ? CorpusIntegrityVerifier.sha256((request.purpose() + "|" + request.engineRevision())
                .getBytes(StandardCharsets.UTF_8)) : run.snapshot().snapshotId();
        EvaluationResultBundle.OverallStatus status = run.status() == BaselineRunResult.RunStatus.REVIEW_REQUIRED
            ? EvaluationResultBundle.OverallStatus.REVIEW_REQUIRED
            : run.status() == BaselineRunResult.RunStatus.PASS && (determinism == null || determinism.deterministic())
                ? EvaluationResultBundle.OverallStatus.PASS : EvaluationResultBundle.OverallStatus.FAIL;
        String bundleId = "u01-" + inputIdentity.substring(0, 16) + '-' + request.purpose().name().toLowerCase();
        List<BaselineDiagnostic> diagnostics = run.diagnostics();
        return new EvaluationResultBundle(1, bundleId, inputIdentity, status, run, determinism, diagnostics);
    }
}
