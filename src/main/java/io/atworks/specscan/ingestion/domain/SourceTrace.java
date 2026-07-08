package io.atworks.specscan.ingestion.domain;

public record SourceTrace(
    String fileRelativePath,
    int startLine,
    int endLine
) {}
