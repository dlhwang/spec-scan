package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.domain.fact.FactGraphDiagnostic;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CanonicalSnapshotBuilder {
    public BaselineObservation.CorpusRunObservation canonicalize(JavaBaselineAnalysisAdapter.RawCorpusObservation raw,
                                                                 CorpusManifest manifest) {
        Map<String, BaselineObservation.ScenarioIdentity> scenarios = scenarioIndex(manifest, raw.corpusId());
        List<BaselineObservation.EndpointObservation> endpoints = new ArrayList<>();
        for (JavaBaselineAnalysisAdapter.RawEndpointObservation endpoint : raw.endpoints()) {
            String operationKey = OperationKey.of(endpoint.endpoint()).externalKey();
            List<BaselineObservation.ObservedCandidate> candidates = endpoint.candidates().stream()
                .map(candidate -> canonicalCandidate(raw.corpusId(), operationKey, candidate, scenarios))
                .sorted(Comparator.comparing(candidate -> candidate.exactIdentity().stableKey())).toList();
            BaselineObservation.GraphStatus graphStatus = endpoint.graph() == null
                ? BaselineObservation.GraphStatus.UNAVAILABLE : BaselineObservation.GraphStatus.COMPLETE;
            endpoints.add(new BaselineObservation.EndpointObservation(operationKey, graphStatus,
                candidates.size(), candidates,
                endpoint.diagnostics().stream().sorted(BaselineDiagnostic.canonicalOrder()).toList()));
        }
        return new BaselineObservation.CorpusRunObservation(raw.corpusId(), raw.sourceDigest(),
            BaselineObservation.AvailabilityStatus.AVAILABLE,
            endpoints.stream().sorted(Comparator.comparing(BaselineObservation.EndpointObservation::operationKey)).toList(),
            raw.diagnostics().stream().sorted(BaselineDiagnostic.canonicalOrder()).toList());
    }

    public String snapshotId(String manifestVersion, String engineRevision,
                             List<BaselineObservation.CorpusRunObservation> corpora) {
        StringBuilder canonical = new StringBuilder(manifestVersion).append('|').append(engineRevision);
        corpora.stream().sorted(Comparator.comparing(BaselineObservation.CorpusRunObservation::corpusId))
            .forEach(corpus -> {
                canonical.append('|').append(corpus.corpusId()).append(':').append(corpus.sourceDigest())
                    .append(':').append(corpus.availabilityStatus());
                corpus.endpoints().stream()
                    .sorted(Comparator.comparing(BaselineObservation.EndpointObservation::operationKey))
                    .forEach(endpoint -> {
                    canonical.append('|').append(endpoint.operationKey()).append(':').append(endpoint.graphStatus());
                    endpoint.candidates().stream()
                        .sorted(Comparator.comparing(candidate -> candidate.exactIdentity().stableKey()))
                        .forEach(candidate -> canonical.append('|')
                        .append(candidate.exactIdentity().stableKey()).append(':')
                        .append(candidate.scenarioIdentity() == null ? "" : candidate.scenarioIdentity().stableKey())
                        .append(':').append(candidate.observation()));
                });
            });
        return CorpusIntegrityVerifier.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private BaselineObservation.ObservedCandidate canonicalCandidate(String corpusId, String operationKey,
                                                                      BusinessRuleCandidate candidate,
                                                                      Map<String, BaselineObservation.ScenarioIdentity> scenarios) {
        NormalizedConstraint constraint = candidate.constraint();
        List<String> values = constraint == null ? List.of() : constraint.expectedValues().stream().sorted().toList();
        String constraintCanonical = constraint == null ? "none" : String.join("|",
            constraint.kind().name(), Objects.toString(constraint.targetPath(), ""),
            Objects.toString(constraint.operator(), ""), String.join(",", values));
        String constraintDigest = CorpusIntegrityVerifier.sha256(constraintCanonical.getBytes(StandardCharsets.UTF_8));
        String evidenceCanonical = candidate.evidence().stream()
            .sorted(Comparator.comparing(EvidenceRef::filePath).thenComparingInt(EvidenceRef::startLine)
                .thenComparing(ref -> ref.role().name()).thenComparing(EvidenceRef::nodeId))
            .map(ref -> ref.filePath() + ':' + ref.startLine() + ':' + ref.role() + ':' + ref.nodeId())
            .reduce("", (left, right) -> left + '|' + right);
        String evidenceDigest = CorpusIntegrityVerifier.sha256(evidenceCanonical.getBytes(StandardCharsets.UTF_8));
        List<String> diagnosticCodes = candidate.diagnostics().stream().map(CandidateDiagnostic::code).sorted().toList();
        BaselineObservation.SemanticObservation semantic = new BaselineObservation.SemanticObservation(
            candidate.ruleId(), candidate.category().name(), candidate.effect().name(),
            constraint == null ? null : constraint.kind().name(),
            constraint == null ? null : constraint.targetPath(), constraint == null ? null : constraint.operator(),
            values, candidate.extractionStatus().name(), candidate.semanticStatus().name(),
            candidate.targetStatus().name(), diagnosticCodes, evidenceDigest);
        BaselineObservation.ObservationIdentity identity = new BaselineObservation.ObservationIdentity(corpusId,
            operationKey, candidate.predicateCandidateId(), candidate.ruleId(), constraintDigest, evidenceDigest);
        BaselineObservation.ScenarioIdentity scenario = scenarios.get("operation:" + operationKey);
        if (scenario == null) scenario = scenarios.get("rule:" + Objects.toString(candidate.ruleId(), ""));
        String source = candidate.evidence().stream().map(EvidenceRef::filePath).sorted().findFirst().orElse(null);
        return new BaselineObservation.ObservedCandidate(identity, scenario, semantic, source);
    }

    private Map<String, BaselineObservation.ScenarioIdentity> scenarioIndex(CorpusManifest manifest, String corpusId) {
        Map<String, BaselineObservation.ScenarioIdentity> result = new HashMap<>();
        for (CorpusManifest.ScenarioDefinition scenario : manifest.scenarios()) {
            if (!scenario.baseCorpusId().equals(corpusId) && !scenario.variantCorpusIds().contains(corpusId)) continue;
            for (CorpusManifest.ScenarioRole role : scenario.roles()) {
                BaselineObservation.ScenarioIdentity identity = new BaselineObservation.ScenarioIdentity(
                    scenario.scenarioId(), role.semanticRole(), role.occurrenceKey());
                String operationKey = role.operationKeysByCorpus().get(corpusId);
                if (operationKey != null) result.put("operation:" + operationKey, identity);
                result.put("rule:" + role.expectedSemanticShape(), identity);
            }
        }
        return result;
    }
}
