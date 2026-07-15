package io.atworks.specscan.ingestion.adapter;

import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.port.RepositoryFetcherPort;

import java.nio.file.Path;

public final class RepositorySourceFetcherAdapter implements RepositoryFetcherPort {
    private final RepositoryFetcherPort gitFetcher = new GitRepositoryFetcherAdapter();
    private final RepositoryFetcherPort localFetcher = new LocalRepositoryFetcherAdapter();

    @Override
    public void fetch(RepositoryIdentity identity, Path targetPath) throws IngestionException {
        if ("local".equals(identity.host())) {
            localFetcher.fetch(identity, targetPath);
        } else {
            gitFetcher.fetch(identity, targetPath);
        }
    }
}
