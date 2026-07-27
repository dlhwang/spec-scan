package io.atworks.specscan.analysis.domain.evaluation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CorpusManifest(int schemaVersion, String manifestId, String version,
                             List<CorpusEntry> corpora, List<ScenarioDefinition> scenarios,
                             String createdAt) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CorpusManifest {
        requireText(manifestId, "manifestId");
        requireText(version, "version");
        corpora = List.copyOf(Objects.requireNonNull(corpora, "corpora"));
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
    }

    public enum SourceKind { CHECKED_IN_FIXTURE, LOCAL_SNAPSHOT }

    public record CorpusEntry(String corpusId, String displayName, SourceKind sourceKind,
                              String locator, boolean required, String contentDigest,
                              String commitRef, List<String> sourceRoots,
                              EndpointScope expectedEndpointScope, Set<String> tags) {
        public CorpusEntry {
            requireText(corpusId, "corpusId");
            requireText(displayName, "displayName");
            Objects.requireNonNull(sourceKind, "sourceKind");
            requireText(locator, "locator");
            sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
            Objects.requireNonNull(expectedEndpointScope, "expectedEndpointScope");
            tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        }
    }

    public record EndpointScope(int minimumExpectedCount, List<String> expectedOperations,
                                List<String> allowedExclusions) {
        public EndpointScope {
            expectedOperations = List.copyOf(Objects.requireNonNull(expectedOperations, "expectedOperations"));
            allowedExclusions = List.copyOf(Objects.requireNonNull(allowedExclusions, "allowedExclusions"));
        }
    }

    public record ScenarioDefinition(String scenarioId, String baseCorpusId,
                                     List<String> variantCorpusIds, List<ScenarioRole> roles,
                                     List<String> expectedTransformations) {
        public ScenarioDefinition {
            requireText(scenarioId, "scenarioId");
            requireText(baseCorpusId, "baseCorpusId");
            variantCorpusIds = List.copyOf(Objects.requireNonNull(variantCorpusIds, "variantCorpusIds"));
            roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
            expectedTransformations = List.copyOf(Objects.requireNonNull(expectedTransformations,
                "expectedTransformations"));
        }
    }

    public record ScenarioRole(String semanticRole, String occurrenceKey,
                               String expectedTargetTransform, String expectedSemanticShape,
                               java.util.Map<String, String> operationKeysByCorpus) {
        public ScenarioRole {
            requireText(semanticRole, "semanticRole");
            requireText(occurrenceKey, "occurrenceKey");
            requireText(expectedSemanticShape, "expectedSemanticShape");
            operationKeysByCorpus = java.util.Map.copyOf(Objects.requireNonNull(
                operationKeysByCorpus, "operationKeysByCorpus"));
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
