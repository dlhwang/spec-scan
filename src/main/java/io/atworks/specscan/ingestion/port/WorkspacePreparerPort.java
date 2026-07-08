package io.atworks.specscan.ingestion.port;

import io.atworks.specscan.ingestion.domain.WorkspaceContext;
import io.atworks.specscan.ingestion.domain.IngestionException;

public interface WorkspacePreparerPort {
    WorkspaceContext prepare() throws IngestionException;
    void clean(WorkspaceContext context);
}
