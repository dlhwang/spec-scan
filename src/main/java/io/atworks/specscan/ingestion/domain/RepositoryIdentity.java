package io.atworks.specscan.ingestion.domain;

public record RepositoryIdentity(
    String host,
    String owner,
    String repositoryName,
    String normalizedCloneUrl,
    String requestedRef
) {}
