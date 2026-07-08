package io.atworks.specscan.ingestion.domain;

public record RepositoryRequest(
    String repositoryUrl,
    String branch,
    String tag,
    String commit
) {
    public RepositoryRequest(String repositoryUrl) {
        this(repositoryUrl, null, null, null);
    }
}
