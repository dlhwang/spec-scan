package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class BaselineManifestContractTest {
    @TempDir Path temporary;

    @Test void readsStrictV1CorpusAndValidatesFiveRequiredRepositories() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        CorpusManifest manifest = new BaselineJsonCodec().readCorpusManifest(workspace.manifest());
        assertThat(new BaselineManifestValidator().validateCorpus(manifest)).isEmpty();
        assertThat(manifest.corpora()).filteredOn(CorpusManifest.CorpusEntry::required).hasSize(6);
        assertThat(manifest.corpora()).filteredOn(entry -> entry.sourceKind()
            == CorpusManifest.SourceKind.CHECKED_IN_FIXTURE).hasSize(6);
        assertThat(manifest.scenarios()).singleElement().satisfies(scenario ->
            assertThat(scenario.variantCorpusIds()).containsExactly("catalog-renamed-holdout"));
    }

    @Test void rejectsUnknownFieldAndUnsupportedReaderVersion() throws Exception {
        Path invalid = temporary.resolve("invalid.json");
        Files.writeString(invalid, "{\"schemaVersion\":1,\"manifestId\":\"x\",\"version\":\"v\","
            + "\"corpora\":[],\"scenarios\":[],\"unknown\":true}");
        assertThatThrownBy(() -> new BaselineJsonCodec().readCorpusManifest(invalid))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown");
        assertThatThrownBy(() -> SchemaVersionReaderRegistry.v1(new BaselineJsonCodec()).read(
            SchemaVersionReaderRegistry.ArtifactKind.CORPUS_MANIFEST, 2, invalid, CorpusManifest.class))
            .hasMessageContaining("UNSUPPORTED_SCHEMA_VERSION");
    }

    @Test void enforcesDispositionRationaleAndNoSilentMigration() {
        var identity = new BaselineObservation.ObservationIdentity("c", "GET /x", "p", "r",
            "a".repeat(64), "b".repeat(64));
        var entry = new BaselineManifest.BaselineEntry("e", identity, null,
            BaselineManifest.Disposition.REPLACE, "", null, null, "source");
        var baseline = new BaselineManifest(1, "b1", "c1", "s1", List.of(entry),
            new BaselineManifest.ApprovalMetadata(BaselineManifest.ApprovalState.PENDING_REVIEW,
                null, null, null));
        assertThat(new BaselineManifestValidator().validateBaseline(baseline))
            .extracting(BaselineDiagnostic::code)
            .contains("BASELINE_RATIONALE_REQUIRED", "CORRECTED_EXPECTATION_REQUIRED");
        Path source = temporary.resolve("source.json"), target = temporary.resolve("target.json");
        assertThatThrownBy(() -> ArtifactMigrationRegistry.empty().migrate(
            new ArtifactMigrationRegistry.MigrationKey(SchemaVersionReaderRegistry.ArtifactKind.BASELINE_MANIFEST, 1, 2),
            source, target)).hasMessageContaining("MIGRATION_PATH_NOT_FOUND");
    }

    @Test void rejectsDuplicateEntryAndExactIdentity() {
        var identity = new BaselineObservation.ObservationIdentity("c", "GET /x", "p", "r",
            "a".repeat(64), "b".repeat(64));
        var expected = new BaselineManifest.SemanticExpectation("EXISTENCE", "BUSINESS_RESTRICTION",
            "CONTROL_FLOW_ONLY", null, null, List.of(), "EXTRACTED", "RESOLVED", "NOT_APPLICABLE", true);
        var first = new BaselineManifest.BaselineEntry("duplicate", identity, null,
            BaselineManifest.Disposition.PRESERVE, "reviewed", expected, null, "source");
        var second = new BaselineManifest.BaselineEntry("duplicate", identity, null,
            BaselineManifest.Disposition.PRESERVE, "reviewed", expected, null, "source");
        var baseline = new BaselineManifest(1, "b1", "c1", "s1", List.of(first, second),
            new BaselineManifest.ApprovalMetadata(BaselineManifest.ApprovalState.APPROVED,
                "reviewer", "2026-07-22T00:00:00Z", "reviewed"));
        assertThat(new BaselineManifestValidator().validateBaseline(baseline))
            .extracting(BaselineDiagnostic::code)
            .contains("DUPLICATE_BASELINE_ENTRY_ID", "DUPLICATE_EXACT_IDENTITY");
    }
}
