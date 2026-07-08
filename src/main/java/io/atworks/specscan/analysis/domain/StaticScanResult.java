package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.IngestionMetadata;
import java.util.List;

public record StaticScanResult(
    List<ApiEndpoint> endpoints,
    int scannedClassesCount,
    List<IngestionWarning> warnings,
    IngestionMetadata metadata
) {}
