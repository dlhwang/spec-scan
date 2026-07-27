package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public final class SingleRunCoordinator {
    @FunctionalInterface public interface CorpusAnalyzer {
        JavaBaselineAnalysisAdapter.RawCorpusObservation analyze(String corpusId,
            EvaluationWorkspaceMaterializer.PreparedEvaluationWorkspace workspace,
            RunDeadline deadline, RunMetricsCollector metrics);
    }
    private record ReadyCorpus(CorpusManifest.CorpusEntry entry,
                               CorpusAccessGuard.ConfinedCorpusView view, String digest) {}

    private final BaselineJsonCodec codec;
    private final SchemaVersionReaderRegistry readers;
    private final BaselineManifestValidator validator;
    private final CorpusAccessGuard accessGuard;
    private final CorpusIntegrityVerifier integrityVerifier;
    private final EvaluationWorkspaceMaterializer materializer;
    private final CorpusAnalyzer analyzer;
    private final CanonicalSnapshotBuilder canonicalizer;
    private final BaselineDiffer differ;
    private final CoverageCalculator coverageCalculator;

    public SingleRunCoordinator() {
        this(new BaselineJsonCodec(), new BaselineManifestValidator(), new CorpusAccessGuard(),
            new CorpusIntegrityVerifier(), new EvaluationWorkspaceMaterializer(),
            new JavaBaselineAnalysisAdapter()::analyze, new CanonicalSnapshotBuilder(),
            new BaselineDiffer(), new CoverageCalculator());
    }

    public SingleRunCoordinator(BaselineJsonCodec codec, BaselineManifestValidator validator,
                                CorpusAccessGuard accessGuard, CorpusIntegrityVerifier integrityVerifier,
                                EvaluationWorkspaceMaterializer materializer, CorpusAnalyzer analyzer,
                                CanonicalSnapshotBuilder canonicalizer, BaselineDiffer differ,
                                CoverageCalculator coverageCalculator) {
        this.codec = codec; this.readers = SchemaVersionReaderRegistry.v1(codec); this.validator = validator;
        this.accessGuard = accessGuard; this.integrityVerifier = integrityVerifier; this.materializer = materializer;
        this.analyzer = analyzer; this.canonicalizer = canonicalizer; this.differ = differ;
        this.coverageCalculator = coverageCalculator;
    }

    public BaselineRunResult run(BaselineRunRequest request) {
        RunMetricsCollector metrics = new RunMetricsCollector();
        RunDeadline deadline = new RunDeadline(Duration.ofMillis(request.profile().singleRunTimeoutMillis()));
        List<BaselineDiagnostic> diagnostics = new ArrayList<>();
        int invocations = 0;
        try {
            CorpusManifest corpusManifest = readers.read(SchemaVersionReaderRegistry.ArtifactKind.CORPUS_MANIFEST,
                codec.readSchemaVersion(request.corpusManifestPath()), request.corpusManifestPath(), CorpusManifest.class);
            diagnostics.addAll(validator.validateCorpus(corpusManifest));
            BaselineManifest baseline = null;
            if (request.purpose() != BaselineRunRequest.RunPurpose.SNAPSHOT_PROPOSAL) {
                baseline = readers.read(SchemaVersionReaderRegistry.ArtifactKind.BASELINE_MANIFEST,
                    codec.readSchemaVersion(request.baselineManifestPath()), request.baselineManifestPath(),
                    BaselineManifest.class);
                diagnostics.addAll(validator.validateApproved(baseline));
                if (!baseline.corpusManifestVersion().equals(corpusManifest.version()))
                    diagnostics.add(error("BASELINE_CORPUS_VERSION_MISMATCH", "preflight", null,
                        "baseline references a different corpus manifest"));
            }
            if (hasErrors(diagnostics)) return failedPreflight(diagnostics, metrics);

            List<ReadyCorpus> ready = new ArrayList<>();
            List<BaselineObservation.CorpusRunObservation> excluded = new ArrayList<>();
            for (CorpusManifest.CorpusEntry corpus : corpusManifest.corpora().stream()
                    .sorted(Comparator.comparing(CorpusManifest.CorpusEntry::corpusId)).toList()) {
                deadline.checkpoint("preflight:" + corpus.corpusId());
                try {
                    BaselineRunRequest.LocalCorpusBinding binding = request.localCorpusBindings().get(corpus.corpusId());
                    if (corpus.sourceKind() == CorpusManifest.SourceKind.LOCAL_SNAPSHOT && binding == null)
                        throw new IllegalStateException("OPTIONAL_LOCAL_BINDING_REQUIRED");
                    Path allowedRoot = binding == null ? request.allowedCorpusRoot() : binding.allowedRoot();
                    String locator = binding == null ? corpus.locator() : binding.locator();
                    String expectedDigest = binding == null ? corpus.contentDigest() : binding.expectedDigest();
                    CorpusAccessGuard.ConfinedCorpusView view = accessGuard.confine(allowedRoot, locator);
                    String digest = integrityVerifier.digest(view);
                    if (!digest.equals(expectedDigest))
                        throw new IllegalStateException("CORPUS_INTEGRITY_MISMATCH");
                    ready.add(new ReadyCorpus(corpus, view, digest));
                } catch (RuntimeException exception) {
                    String code = exception instanceof CorpusAccessGuard.CorpusAccessException access
                        ? access.code() : exception.getMessage() != null && exception.getMessage().startsWith("CORPUS_")
                            ? exception.getMessage() : "CORPUS_UNAVAILABLE";
                    if (corpus.required()) diagnostics.add(error(code, "preflight", corpus.corpusId(), exception.getMessage()));
                    else {
                        BaselineDiagnostic warning = new BaselineDiagnostic("EXCLUDED_OPTIONAL",
                            BaselineDiagnostic.Severity.WARNING, "preflight", corpus.corpusId(), null, null,
                            exception.getMessage() == null ? code : exception.getMessage());
                        diagnostics.add(warning);
                        excluded.add(new BaselineObservation.CorpusRunObservation(corpus.corpusId(),
                            corpus.contentDigest(), BaselineObservation.AvailabilityStatus.EXCLUDED_OPTIONAL,
                            List.of(), List.of(warning)));
                    }
                }
            }
            if (hasErrors(diagnostics)) return failedPreflight(diagnostics, metrics);

            Path runWorkspace = request.evaluationWorkspaceRoot().resolve("run-" + UUID.randomUUID());
            Map<String, EvaluationWorkspaceMaterializer.PreparedEvaluationWorkspace> prepared = new TreeMap<>();
            for (ReadyCorpus corpus : ready) {
                deadline.checkpoint("materialize:" + corpus.entry().corpusId());
                prepared.put(corpus.entry().corpusId(), materializer.materialize(corpus.view(), runWorkspace,
                    corpus.entry().corpusId(), corpus.entry().sourceRoots(), corpus.digest()));
            }

            List<BaselineObservation.CorpusRunObservation> observations = new ArrayList<>(excluded);
            for (ReadyCorpus corpus : ready) {
                deadline.checkpoint("analysis:" + corpus.entry().corpusId());
                metrics.checkpoint("corpus", corpus.entry().corpusId(), null);
                invocations++;
                JavaBaselineAnalysisAdapter.RawCorpusObservation raw = analyzer.analyze(corpus.entry().corpusId(),
                    prepared.get(corpus.entry().corpusId()), deadline, metrics);
                observations.add(canonicalizer.canonicalize(raw, corpusManifest));
            }
            observations = observations.stream()
                .sorted(Comparator.comparing(BaselineObservation.CorpusRunObservation::corpusId)).toList();
            BaselineObservation.CoverageSnapshot coverage = coverageCalculator.calculate(observations);
            diagnostics.addAll(metrics.diagnostics());
            diagnostics = diagnostics.stream().sorted(BaselineDiagnostic.canonicalOrder()).toList();
            String snapshotId = canonicalizer.snapshotId(corpusManifest.version(), request.engineRevision(), observations);
            BaselineObservation.Snapshot snapshot = new BaselineObservation.Snapshot(snapshotId,
                corpusManifest.version(), request.engineRevision(), observations, diagnostics, coverage, metrics.snapshot());
            BaselineDiff.Result diff = request.purpose() == BaselineRunRequest.RunPurpose.SNAPSHOT_PROPOSAL
                ? differ.proposal(snapshot) : differ.compare(snapshot, baseline);
            BaselineRunResult.RunStatus status = request.purpose() == BaselineRunRequest.RunPurpose.SNAPSHOT_PROPOSAL
                ? BaselineRunResult.RunStatus.REVIEW_REQUIRED : diff.verdict() == BaselineDiff.Verdict.PASS
                    ? BaselineRunResult.RunStatus.PASS : BaselineRunResult.RunStatus.FAILED_ANALYSIS;
            List<BaselineDiagnostic> combined = new ArrayList<>(diagnostics); combined.addAll(diff.diagnostics());
            return new BaselineRunResult(status, snapshot, diff,
                combined.stream().sorted(BaselineDiagnostic.canonicalOrder()).toList(), metrics.snapshot(), invocations);
        } catch (RunDeadline.EvaluationTimeoutException exception) {
            diagnostics.add(error("EVALUATION_TIMEOUT", "execution", null, exception.getMessage()));
            diagnostics.addAll(metrics.diagnostics());
            return new BaselineRunResult(BaselineRunResult.RunStatus.FAILED_TIMEOUT, null, null,
                diagnostics, metrics.snapshot(), invocations);
        } catch (RuntimeException exception) {
            diagnostics.add(error("EVALUATION_FAILED", "execution", null,
                exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
            diagnostics.addAll(metrics.diagnostics());
            return new BaselineRunResult(invocations == 0 ? BaselineRunResult.RunStatus.FAILED_PREFLIGHT
                : BaselineRunResult.RunStatus.FAILED_ANALYSIS, null, null, diagnostics, metrics.snapshot(), invocations);
        }
    }

    private BaselineRunResult failedPreflight(List<BaselineDiagnostic> diagnostics, RunMetricsCollector metrics) {
        return new BaselineRunResult(BaselineRunResult.RunStatus.FAILED_PREFLIGHT, null, null,
            diagnostics.stream().sorted(BaselineDiagnostic.canonicalOrder()).toList(), metrics.snapshot(), 0);
    }
    private boolean hasErrors(List<BaselineDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == BaselineDiagnostic.Severity.ERROR);
    }
    private BaselineDiagnostic error(String code, String stage, String corpus, String details) {
        return new BaselineDiagnostic(code, BaselineDiagnostic.Severity.ERROR, stage, corpus, null, null,
            details == null || details.isBlank() ? code : details);
    }
}
