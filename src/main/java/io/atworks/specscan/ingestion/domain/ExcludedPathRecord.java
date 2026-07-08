package io.atworks.specscan.ingestion.domain;

public record ExcludedPathRecord(
    String path,
    String reasonCode,
    String reasonMessage
) {}
