package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BaselineCanonicalDiffTest {
    @Test void proposalDoesNotPromoteCurrentObservationToTruth() {
        BaselineObservation.Snapshot snapshot = snapshot();
        BaselineDiff.Result result = new BaselineDiffer().proposal(snapshot);
        assertThat(result.verdict()).isEqualTo(BaselineDiff.Verdict.REVIEW_REQUIRED);
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(BaselineDiff.ChangeType.ADDED);
            assertThat(change.disposition()).isNull();
        });
    }

    @Test void graphAndSemanticCoverageRemainSeparate() {
        BaselineObservation.CoverageSnapshot coverage = new CoverageCalculator().calculate(snapshot().corpusRuns());
        assertThat(coverage.graph().ratio()).isEqualTo(1.0);
        assertThat(coverage.semantic().ratio()).isEqualTo(1.0);
        assertThat(coverage.graph()).isNotSameAs(coverage.semantic());
    }

    @Test void exactDiffReportsAddedRemovedChangedAndUnchanged() {
        var unchanged = candidate("unchanged", "RULE", "EXISTENCE", null);
        var changed = candidate("changed", "RULE", "UNKNOWN", null);
        var added = candidate("added", "RULE", "EXISTENCE", null);
        var removed = candidate("removed", "RULE", "EXISTENCE", null);
        BaselineManifest baseline = baseline(
            entry("keep", unchanged.exactIdentity(), null, expectation("EXISTENCE")),
            entry("change", changed.exactIdentity(), null, expectation("EXISTENCE")),
            entry("remove", removed.exactIdentity(), null, expectation("EXISTENCE")));

        BaselineDiff.Result result = new BaselineDiffer().compare(snapshot(unchanged, changed, added), baseline);

        assertThat(result.changes()).extracting(BaselineDiff.Change::type)
            .containsExactlyInAnyOrder(BaselineDiff.ChangeType.UNCHANGED, BaselineDiff.ChangeType.CHANGED,
                BaselineDiff.ChangeType.ADDED, BaselineDiff.ChangeType.REMOVED);
        assertThat(result.verdict()).isEqualTo(BaselineDiff.Verdict.FAIL);
    }

    @Test void scenarioIdentityMapsSemanticallyEquivalentRenameHoldout() {
        var scenario = new BaselineObservation.ScenarioIdentity("rename", "required-lookup", "primary");
        var renamed = candidate("renamed-exact-identity", "RULE", "EXISTENCE", scenario);
        BaselineManifest baseline = baseline(entry("scenario", null, scenario, expectation("EXISTENCE")));

        BaselineDiff.Result result = new BaselineDiffer().compare(snapshot(renamed), baseline);

        assertThat(result.verdict()).isEqualTo(BaselineDiff.Verdict.PASS);
        assertThat(result.changes()).singleElement().extracting(BaselineDiff.Change::type)
            .isEqualTo(BaselineDiff.ChangeType.UNCHANGED);
    }

    @Test void duplicateExactIdentityFailsInsteadOfOverwritingObservation() {
        var duplicate = candidate("same", "RULE", "EXISTENCE", null);
        BaselineDiff.Result result = new BaselineDiffer().compare(snapshot(duplicate, duplicate), baseline());
        assertThat(result.verdict()).isEqualTo(BaselineDiff.Verdict.FAIL);
        assertThat(result.diagnostics()).extracting(BaselineDiagnostic::code)
            .containsExactly("BASELINE_IDENTITY_COLLISION");
    }

    @Test void canonicalSnapshotIgnoresInputOrderAndHostSourceReference() {
        var first = candidate("a", "RULE", "EXISTENCE", null);
        var moved = new BaselineObservation.ObservedCandidate(first.exactIdentity(), first.scenarioIdentity(),
            first.observation(), "D:/different-host/path/X.java");
        var second = candidate("b", "RULE", "EXISTENCE", null);
        var endpointA = new BaselineObservation.EndpointObservation("GET /a",
            BaselineObservation.GraphStatus.COMPLETE, 1, List.of(first), List.of());
        var endpointB = new BaselineObservation.EndpointObservation("GET /b",
            BaselineObservation.GraphStatus.COMPLETE, 1, List.of(second), List.of());
        var ordered = corpus(List.of(endpointA, endpointB));
        var shuffled = corpus(List.of(endpointB, new BaselineObservation.EndpointObservation("GET /a",
            BaselineObservation.GraphStatus.COMPLETE, 1, List.of(moved), List.of())));
        CanonicalSnapshotBuilder builder = new CanonicalSnapshotBuilder();
        assertThat(builder.snapshotId("m", "e", List.of(ordered)))
            .isEqualTo(builder.snapshotId("m", "e", List.of(shuffled)));
    }

    static BaselineObservation.Snapshot snapshot() {
        var semantic = new BaselineObservation.SemanticObservation("RULE", "EXISTENCE", "BUSINESS_RESTRICTION",
            "CONTROL_FLOW_ONLY", null, null, List.of(), "EXTRACTED", "RESOLVED", "NOT_APPLICABLE",
            List.of(), "b".repeat(64));
        var identity = new BaselineObservation.ObservationIdentity("c", "GET /x", "p", "RULE",
            "a".repeat(64), "b".repeat(64));
        var candidate = new BaselineObservation.ObservedCandidate(identity, null, semantic, "src/X.java");
        var endpoint = new BaselineObservation.EndpointObservation("GET /x", BaselineObservation.GraphStatus.COMPLETE,
            1, List.of(candidate), List.of());
        var corpus = new BaselineObservation.CorpusRunObservation("c", "d".repeat(64),
            BaselineObservation.AvailabilityStatus.AVAILABLE, List.of(endpoint), List.of());
        var coverage = new CoverageCalculator().calculate(List.of(corpus));
        return new BaselineObservation.Snapshot("s", "m", "e", List.of(corpus), List.of(), coverage,
            BaselineObservation.MetricsSnapshot.empty());
    }

    private static BaselineObservation.Snapshot snapshot(BaselineObservation.ObservedCandidate... candidates) {
        var endpoint = new BaselineObservation.EndpointObservation("GET /x", BaselineObservation.GraphStatus.COMPLETE,
            candidates.length, List.of(candidates), List.of());
        var corpus = new BaselineObservation.CorpusRunObservation("c", "d".repeat(64),
            BaselineObservation.AvailabilityStatus.AVAILABLE, List.of(endpoint), List.of());
        var coverage = new CoverageCalculator().calculate(List.of(corpus));
        return new BaselineObservation.Snapshot("s", "m", "e", List.of(corpus), List.of(), coverage,
            BaselineObservation.MetricsSnapshot.empty());
    }

    private static BaselineObservation.ObservedCandidate candidate(String candidateId, String ruleId,
                                                                    String category,
                                                                    BaselineObservation.ScenarioIdentity scenario) {
        var semantic = new BaselineObservation.SemanticObservation(ruleId, category, "BUSINESS_RESTRICTION",
            "CONTROL_FLOW_ONLY", null, null, List.of(), "EXTRACTED", "RESOLVED", "NOT_APPLICABLE",
            List.of(), "b".repeat(64));
        var identity = new BaselineObservation.ObservationIdentity("c", "GET /x", candidateId, ruleId,
            "a".repeat(64), "b".repeat(64));
        return new BaselineObservation.ObservedCandidate(identity, scenario, semantic, "src/X.java");
    }

    private static BaselineManifest.SemanticExpectation expectation(String category) {
        return new BaselineManifest.SemanticExpectation(category, "BUSINESS_RESTRICTION", "CONTROL_FLOW_ONLY",
            null, null, List.of(), "EXTRACTED", "RESOLVED", "NOT_APPLICABLE", true);
    }

    private static BaselineManifest.BaselineEntry entry(String id,
                                                         BaselineObservation.ObservationIdentity exact,
                                                         BaselineObservation.ScenarioIdentity scenario,
                                                         BaselineManifest.SemanticExpectation expectation) {
        return new BaselineManifest.BaselineEntry(id, exact, scenario, BaselineManifest.Disposition.PRESERVE,
            "reviewed expectation", expectation, null, "proposal");
    }

    private static BaselineManifest baseline(BaselineManifest.BaselineEntry... entries) {
        return new BaselineManifest(1, "v1", "m", "s", List.of(entries),
            new BaselineManifest.ApprovalMetadata(BaselineManifest.ApprovalState.APPROVED,
                "reviewer", "2026-07-22T00:00:00Z", "reviewed"));
    }

    private static BaselineObservation.CorpusRunObservation corpus(
            List<BaselineObservation.EndpointObservation> endpoints) {
        return new BaselineObservation.CorpusRunObservation("c", "d".repeat(64),
            BaselineObservation.AvailabilityStatus.AVAILABLE, endpoints, List.of());
    }
}
