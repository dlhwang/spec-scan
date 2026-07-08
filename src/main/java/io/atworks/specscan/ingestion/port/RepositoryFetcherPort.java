package io.atworks.specscan.ingestion.port;

import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.domain.IngestionException;
import java.nio.file.Path;

public interface RepositoryFetcherPort {
    void fetch(RepositoryIdentity identity, Path targetPath) throws IngestionException;
}
