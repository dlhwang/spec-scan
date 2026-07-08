package io.atworks.specscan.ingestion.domain;

import java.time.Instant;

public record WorkspaceContext(
    String executionId,
    String workspacePath,
    Instant createdAt,
    String cacheKey,
    boolean cacheEligible
) {}
