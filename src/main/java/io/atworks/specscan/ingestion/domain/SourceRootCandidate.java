package io.atworks.specscan.ingestion.domain;

public record SourceRootCandidate(
    String moduleName,
    String rootPath,
    String buildToolHint,
    boolean hasMainJava,
    int javaFileCount,
    int springAnnotationCandidateCount,
    int selectionPriority,
    String status
) {}
