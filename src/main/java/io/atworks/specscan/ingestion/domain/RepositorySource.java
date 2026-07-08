package io.atworks.specscan.ingestion.domain;

import java.util.List;

public record RepositorySource(
    RepositoryIdentity repositoryIdentity,
    WorkspaceContext workspaceContext,
    List<SourceRootCandidate> sourceRoots,
    String buildToolHint,
    JavaInventorySummary javaInventorySummary,
    List<ExcludedPathRecord> excludedPaths,
    List<IngestionWarning> warnings,
    SafetyPolicyHint safetyPolicyHint,
    IngestionMetadata ingestionMetadata
) {}
