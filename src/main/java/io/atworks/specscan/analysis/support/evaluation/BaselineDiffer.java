package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BaselineDiffer {
    public BaselineDiff.Result proposal(BaselineObservation.Snapshot snapshot) {
        List<BaselineDiff.Change> changes = observations(snapshot).stream().map(candidate ->
            new BaselineDiff.Change(candidate.exactIdentity().stableKey(), BaselineDiff.ChangeType.ADDED, null,
                null, candidate.observation(), BaselineDiff.Verdict.REVIEW_REQUIRED,
                "observation requires disposition and rationale review")).toList();
        String proposalId = CorpusIntegrityVerifier.sha256((snapshot.snapshotId() + "|proposal")
            .getBytes(StandardCharsets.UTF_8));
        BaselineDiff.ChangeProposal proposal = new BaselineDiff.ChangeProposal(proposalId,
            "PENDING_REVIEW", changes, "Current Java observations are facts, not approved truth.");
        List<BaselineDiagnostic> diagnostics = changes.isEmpty() ? List.of() : List.of(new BaselineDiagnostic(
            "BASELINE_ENTRY_UNLABELED", BaselineDiagnostic.Severity.ERROR, "diff", null, null, null,
            changes.size() + " observations require review"));
        return BaselineDiff.Result.reviewProposal(proposal, diagnostics);
    }

    public BaselineDiff.Result compare(BaselineObservation.Snapshot snapshot, BaselineManifest baseline) {
        Map<String, BaselineManifest.BaselineEntry> expectedExact = new TreeMap<>();
        Map<String, BaselineManifest.BaselineEntry> expectedScenario = new TreeMap<>();
        baseline.entries().forEach(entry -> {
            if (entry.exactIdentity() != null) expectedExact.put(entry.exactIdentity().stableKey(), entry);
            if (entry.scenarioIdentity() != null) expectedScenario.put(entry.scenarioIdentity().stableKey(), entry);
        });
        List<BaselineDiff.Change> changes = new ArrayList<>();
        List<BaselineDiagnostic> diagnostics = new ArrayList<>();
        Set<String> observedExact = new HashSet<>();
        Set<String> matchedEntries = new HashSet<>();
        for (BaselineObservation.ObservedCandidate actual : observations(snapshot)) {
            String identity = actual.exactIdentity().stableKey();
            if (!observedExact.add(identity)) {
                diagnostics.add(new BaselineDiagnostic("BASELINE_IDENTITY_COLLISION",
                    BaselineDiagnostic.Severity.ERROR, "diff", actual.exactIdentity().corpusId(),
                    actual.exactIdentity().operationKey(), identity, "duplicate exact observation identity"));
                continue;
            }
            BaselineManifest.BaselineEntry entry = expectedExact.get(identity);
            if (entry == null && actual.scenarioIdentity() != null) {
                entry = expectedScenario.get(actual.scenarioIdentity().stableKey());
            }
            if (entry == null) {
                changes.add(new BaselineDiff.Change(identity, BaselineDiff.ChangeType.ADDED, null, null,
                    actual.observation(), BaselineDiff.Verdict.REVIEW_REQUIRED, "unlabeled observation"));
            } else {
                matchedEntries.add(entry.entryId());
                boolean matches = matches(entry, actual.observation());
                changes.add(new BaselineDiff.Change(identity, matches ? BaselineDiff.ChangeType.UNCHANGED
                    : BaselineDiff.ChangeType.CHANGED, entry.disposition(), expectedSemantic(entry),
                    actual.observation(), matches ? BaselineDiff.Verdict.PASS : BaselineDiff.Verdict.FAIL,
                    matches ? "approved expectation matched" : "semantic expectation changed"));
            }
        }
        baseline.entries().stream().filter(entry -> !matchedEntries.contains(entry.entryId())).forEach(entry -> {
            String identity = entry.exactIdentity() != null ? entry.exactIdentity().stableKey()
                : "scenario:" + entry.scenarioIdentity().stableKey();
            changes.add(new BaselineDiff.Change(identity, BaselineDiff.ChangeType.REMOVED, entry.disposition(),
                expectedSemantic(entry), null, BaselineDiff.Verdict.FAIL, "approved observation is missing"));
        });
        changes.sort(Comparator.comparing(BaselineDiff.Change::identity));
        diagnostics.sort(BaselineDiagnostic.canonicalOrder());
        BaselineDiff.Verdict verdict = !diagnostics.isEmpty()
            ? BaselineDiff.Verdict.FAIL
            : changes.stream().anyMatch(change -> change.verdict() == BaselineDiff.Verdict.FAIL)
            ? BaselineDiff.Verdict.FAIL : changes.stream().anyMatch(change -> change.verdict() == BaselineDiff.Verdict.REVIEW_REQUIRED)
                ? BaselineDiff.Verdict.REVIEW_REQUIRED : BaselineDiff.Verdict.PASS;
        return new BaselineDiff.Result(changes, diagnostics, null, verdict);
    }

    private boolean matches(BaselineManifest.BaselineEntry entry,
                            BaselineObservation.SemanticObservation actual) {
        if (entry.disposition() == BaselineManifest.Disposition.UNSUPPORTED) {
            BaselineManifest.DiagnosticExpectation expected = entry.expectedDiagnostic();
            return expected != null && Objects.equals(expected.status(), actual.semanticStatus())
                && actual.diagnosticCodes().contains(expected.diagnosticCode());
        }
        BaselineManifest.SemanticExpectation expected = entry.expectedObservation();
        return expected != null && Objects.equals(expected.category(), actual.category())
            && Objects.equals(expected.effect(), actual.effect())
            && Objects.equals(expected.constraintKind(), actual.constraintKind())
            && Objects.equals(expected.targetPath(), actual.targetPath())
            && Objects.equals(expected.operator(), actual.operator())
            && Objects.equals(expected.expectedValues().stream().sorted().toList(), actual.expectedValues())
            && Objects.equals(expected.extractionStatus(), actual.extractionStatus())
            && Objects.equals(expected.semanticStatus(), actual.semanticStatus())
            && Objects.equals(expected.targetResolutionStatus(), actual.targetResolutionStatus())
            && (!expected.evidenceRequired() || actual.evidenceFingerprint() != null);
    }

    private BaselineObservation.SemanticObservation expectedSemantic(BaselineManifest.BaselineEntry entry) {
        BaselineManifest.SemanticExpectation expected = entry.expectedObservation();
        if (expected == null) return null;
        return new BaselineObservation.SemanticObservation(null, expected.category(), expected.effect(),
            expected.constraintKind(), expected.targetPath(), expected.operator(), expected.expectedValues(),
            expected.extractionStatus(), expected.semanticStatus(), expected.targetResolutionStatus(), List.of(), null);
    }

    private List<BaselineObservation.ObservedCandidate> observations(BaselineObservation.Snapshot snapshot) {
        return snapshot.corpusRuns().stream().flatMap(corpus -> corpus.endpoints().stream())
            .flatMap(endpoint -> endpoint.candidates().stream())
            .sorted(Comparator.comparing(candidate -> candidate.exactIdentity().stableKey())).toList();
    }
}
