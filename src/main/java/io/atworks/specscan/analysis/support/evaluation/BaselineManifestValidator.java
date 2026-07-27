package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.util.*;

public final class BaselineManifestValidator {
    public List<BaselineDiagnostic> validateCorpus(CorpusManifest manifest) {
        List<BaselineDiagnostic> diagnostics = new ArrayList<>();
        if (manifest.schemaVersion() != CorpusManifest.CURRENT_SCHEMA_VERSION)
            error(diagnostics, "UNSUPPORTED_SCHEMA_VERSION", "manifest", null, "unsupported corpus schema");
        Set<String> corpusIds = new HashSet<>();
        for (CorpusManifest.CorpusEntry corpus : manifest.corpora()) {
            if (!corpusIds.add(corpus.corpusId()))
                error(diagnostics, "DUPLICATE_CORPUS_ID", "manifest", corpus.corpusId(), "duplicate corpus ID");
            if (corpus.contentDigest() == null || !corpus.contentDigest().matches("[0-9a-f]{64}"))
                error(diagnostics, "CORPUS_DIGEST_REQUIRED", "manifest", corpus.corpusId(), "lowercase SHA-256 is required");
            if (corpus.sourceRoots().isEmpty())
                error(diagnostics, "CORPUS_SOURCE_ROOT_REQUIRED", "manifest", corpus.corpusId(), "source root is required");
            if (corpus.expectedEndpointScope().minimumExpectedCount() <= 0)
                error(diagnostics, "CORPUS_SCOPE_INVALID", "manifest", corpus.corpusId(), "positive endpoint scope is required");
        }
        long checkedInRequired = manifest.corpora().stream().filter(CorpusManifest.CorpusEntry::required)
            .filter(corpus -> corpus.sourceKind() == CorpusManifest.SourceKind.CHECKED_IN_FIXTURE).count();
        if (checkedInRequired < 5 || manifest.corpora().size() > 10)
            error(diagnostics, "CORPUS_COUNT_OUT_OF_RANGE", "manifest", null,
                "five required checked-in corpora and at most ten total corpora are required");
        Set<String> scenarioIds = new HashSet<>();
        for (CorpusManifest.ScenarioDefinition scenario : manifest.scenarios()) {
            if (!scenarioIds.add(scenario.scenarioId()))
                error(diagnostics, "DUPLICATE_SCENARIO_ID", "manifest", null, "duplicate scenario ID");
            if (!corpusIds.contains(scenario.baseCorpusId()) || scenario.variantCorpusIds().isEmpty()
                    || scenario.variantCorpusIds().stream().anyMatch(id -> !corpusIds.contains(id))
                    || scenario.roles().isEmpty())
                error(diagnostics, "HOLDOUT_MAPPING_INCOMPLETE", "manifest", scenario.baseCorpusId(),
                    "base, variant and role mappings must be complete");
            Set<String> participatingCorpora = new HashSet<>(scenario.variantCorpusIds());
            participatingCorpora.add(scenario.baseCorpusId());
            for (CorpusManifest.ScenarioRole role : scenario.roles()) {
                if (!role.operationKeysByCorpus().keySet().equals(participatingCorpora)
                        || role.operationKeysByCorpus().values().stream().anyMatch(value -> value == null || value.isBlank()))
                    error(diagnostics, "HOLDOUT_OPERATION_MAPPING_INCOMPLETE", "manifest",
                        scenario.baseCorpusId(), "each scenario role must map every participating corpus operation");
            }
        }
        return diagnostics.stream().sorted(BaselineDiagnostic.canonicalOrder()).toList();
    }

    public List<BaselineDiagnostic> validateBaseline(BaselineManifest baseline) {
        List<BaselineDiagnostic> diagnostics = new ArrayList<>();
        if (baseline.schemaVersion() != BaselineManifest.CURRENT_SCHEMA_VERSION)
            error(diagnostics, "UNSUPPORTED_SCHEMA_VERSION", "baseline", null, "unsupported baseline schema");
        Set<String> entryIds = new HashSet<>();
        Set<String> exactIdentities = new HashSet<>();
        Set<String> scenarioIdentities = new HashSet<>();
        for (BaselineManifest.BaselineEntry entry : baseline.entries()) {
            if (!entryIds.add(entry.entryId()))
                error(diagnostics, "DUPLICATE_BASELINE_ENTRY_ID", "baseline", null, entry.entryId());
            if (entry.rationale() == null || entry.rationale().isBlank())
                error(diagnostics, "BASELINE_RATIONALE_REQUIRED", "baseline", null, entry.entryId());
            if (entry.exactIdentity() == null && entry.scenarioIdentity() == null)
                error(diagnostics, "BASELINE_IDENTITY_REQUIRED", "baseline", null, entry.entryId());
            if (entry.exactIdentity() != null && !exactIdentities.add(entry.exactIdentity().stableKey()))
                error(diagnostics, "DUPLICATE_EXACT_IDENTITY", "baseline", null, entry.entryId());
            if (entry.scenarioIdentity() != null && !scenarioIdentities.add(entry.scenarioIdentity().stableKey()))
                error(diagnostics, "DUPLICATE_SCENARIO_IDENTITY", "baseline", null, entry.entryId());
            if (entry.disposition() == BaselineManifest.Disposition.PRESERVE && entry.expectedObservation() == null)
                error(diagnostics, "PRESERVE_EXPECTATION_REQUIRED", "baseline", null, entry.entryId());
            if (entry.disposition() == BaselineManifest.Disposition.REPLACE && entry.expectedObservation() == null)
                error(diagnostics, "CORRECTED_EXPECTATION_REQUIRED", "baseline", null, entry.entryId());
            if (entry.disposition() == BaselineManifest.Disposition.UNSUPPORTED && entry.expectedDiagnostic() == null)
                error(diagnostics, "UNSUPPORTED_DIAGNOSTIC_REQUIRED", "baseline", null, entry.entryId());
            if (entry.disposition() == BaselineManifest.Disposition.PRESERVE && looksDelegatedHardcoded(entry))
                error(diagnostics, "DELEGATED_HARDCODING_CANNOT_BE_PRESERVED", "baseline", null, entry.entryId());
        }
        return diagnostics.stream().sorted(BaselineDiagnostic.canonicalOrder()).toList();
    }

    public List<BaselineDiagnostic> validateApproved(BaselineManifest baseline) {
        List<BaselineDiagnostic> diagnostics = new ArrayList<>(validateBaseline(baseline));
        if (baseline.approval().state() != BaselineManifest.ApprovalState.APPROVED)
            error(diagnostics, "BASELINE_NOT_APPROVED", "baseline", null, "approved baseline required");
        return diagnostics.stream().sorted(BaselineDiagnostic.canonicalOrder()).toList();
    }

    private boolean looksDelegatedHardcoded(BaselineManifest.BaselineEntry entry) {
        BaselineManifest.SemanticExpectation expected = entry.expectedObservation();
        if (expected == null) return false;
        String joined = String.join(" ", Objects.toString(expected.targetPath(), ""),
            Objects.toString(expected.operator(), ""), Objects.toString(entry.rationale(), ""));
        return joined.contains("currentUser") || joined.contains("order.state") || joined.contains("permission");
    }

    private void error(List<BaselineDiagnostic> target, String code, String stage, String corpus, String details) {
        target.add(new BaselineDiagnostic(code, BaselineDiagnostic.Severity.ERROR, stage, corpus,
            null, null, details));
    }
}
