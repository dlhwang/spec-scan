package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;

public final class BaselineObservation {
    private BaselineObservation() {}

    public enum AvailabilityStatus { AVAILABLE, EXCLUDED_OPTIONAL, FAILED_REQUIRED }
    public enum GraphStatus { COMPLETE, PARTIAL, UNAVAILABLE }

    public record Snapshot(String snapshotId, String manifestVersion, String engineRevision,
                           List<CorpusRunObservation> corpusRuns, List<BaselineDiagnostic> diagnostics,
                           CoverageSnapshot coverage, MetricsSnapshot metrics) {
        public Snapshot {
            requireText(snapshotId, "snapshotId"); requireText(manifestVersion, "manifestVersion");
            requireText(engineRevision, "engineRevision");
            corpusRuns = List.copyOf(Objects.requireNonNull(corpusRuns, "corpusRuns"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            Objects.requireNonNull(coverage, "coverage"); Objects.requireNonNull(metrics, "metrics");
        }
    }

    public record CorpusRunObservation(String corpusId, String sourceDigest, AvailabilityStatus availabilityStatus,
                                       List<EndpointObservation> endpoints, List<BaselineDiagnostic> diagnostics) {
        public CorpusRunObservation {
            requireText(corpusId, "corpusId"); Objects.requireNonNull(availabilityStatus, "availabilityStatus");
            endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record EndpointObservation(String operationKey, GraphStatus graphStatus,
                                      int eligibleFailureCandidateCount, List<ObservedCandidate> candidates,
                                      List<BaselineDiagnostic> diagnostics) {
        public EndpointObservation {
            requireText(operationKey, "operationKey"); Objects.requireNonNull(graphStatus, "graphStatus");
            if (eligibleFailureCandidateCount < 0) throw new IllegalArgumentException("candidate count cannot be negative");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record ObservedCandidate(ObservationIdentity exactIdentity, ScenarioIdentity scenarioIdentity,
                                    SemanticObservation observation, String sourceReference) {
        public ObservedCandidate {
            Objects.requireNonNull(exactIdentity, "exactIdentity");
            Objects.requireNonNull(observation, "observation");
        }
    }

    public record SemanticObservation(String ruleId, String category, String effect, String constraintKind,
                                      String targetPath, String operator, List<String> expectedValues,
                                      String extractionStatus, String semanticStatus,
                                      String targetResolutionStatus, List<String> diagnosticCodes,
                                      String evidenceFingerprint) {
        public SemanticObservation {
            expectedValues = List.copyOf(Objects.requireNonNull(expectedValues, "expectedValues"));
            diagnosticCodes = List.copyOf(Objects.requireNonNull(diagnosticCodes, "diagnosticCodes"));
        }
    }

    public record ObservationIdentity(String corpusId, String operationKey, String predicateCandidateId,
                                      String ruleId, String canonicalConstraintDigest,
                                      String evidenceFingerprint) {
        public ObservationIdentity {
            requireText(corpusId, "corpusId"); requireText(operationKey, "operationKey");
            requireText(predicateCandidateId, "predicateCandidateId");
            requireText(canonicalConstraintDigest, "canonicalConstraintDigest");
            requireText(evidenceFingerprint, "evidenceFingerprint");
        }
        public String stableKey() {
            return String.join("|", corpusId, operationKey, predicateCandidateId,
                ruleId == null ? "" : ruleId, canonicalConstraintDigest, evidenceFingerprint);
        }
    }

    public record ScenarioIdentity(String scenarioId, String semanticRole, String occurrenceKey) {
        public ScenarioIdentity {
            requireText(scenarioId, "scenarioId"); requireText(semanticRole, "semanticRole");
            requireText(occurrenceKey, "occurrenceKey");
        }
        public String stableKey() { return String.join("|", scenarioId, semanticRole, occurrenceKey); }
    }

    public record CoverageDimension(int numerator, int denominator, List<String> exclusions) {
        public CoverageDimension {
            if (numerator < 0 || denominator < 0 || numerator > denominator)
                throw new IllegalArgumentException("invalid coverage values");
            exclusions = List.copyOf(Objects.requireNonNull(exclusions, "exclusions"));
        }
        public double ratio() { return denominator == 0 ? 1.0 : (double) numerator / denominator; }
    }

    public record CoverageSnapshot(CoverageDimension graph, CoverageDimension semantic,
                                   int unclassifiedCount, int unresolvedCount, int partialCount) {
        public CoverageSnapshot {
            Objects.requireNonNull(graph, "graph"); Objects.requireNonNull(semantic, "semantic");
            if (unclassifiedCount < 0 || unresolvedCount < 0 || partialCount < 0)
                throw new IllegalArgumentException("coverage counts cannot be negative");
        }
        public static CoverageSnapshot empty() {
            return new CoverageSnapshot(new CoverageDimension(0, 0, List.of()),
                new CoverageDimension(0, 0, List.of()), 0, 0, 0);
        }
    }

    public record MetricsSnapshot(long elapsedNanos, long observedHeapPeakBytes, int graphCount,
                                  long nodeCount, long edgeCount, int candidateCount, boolean memoryAvailable) {
        public static MetricsSnapshot empty() { return new MetricsSnapshot(0, 0, 0, 0, 0, 0, true); }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
