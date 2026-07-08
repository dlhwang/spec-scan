package io.atworks.specscan.ingestion.domain;

public record JavaInventorySummary(
    int totalJavaFileCount,
    int sourceRootCount,
    int moduleCount,
    boolean scanCompleted,
    int partialFailureCount
) {}
