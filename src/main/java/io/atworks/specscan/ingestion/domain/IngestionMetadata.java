package io.atworks.specscan.ingestion.domain;

import java.time.Instant;

public record IngestionMetadata(
    Instant startedAt,
    Instant completedAt,
    long durationMs,
    String requestedRefType,
    String resolvedRef,
    int warningsCount
) {}
