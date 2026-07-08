package io.atworks.specscan.ingestion.domain;

public record IngestionWarning(
    String warningCode,
    String message,
    String relatedPath,
    String severity
) {}
