package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared, immutable input for all endpoint analysis strategies in a single scan run.
 */
public record ScanAnalysisContext(
    RepositorySource repositorySource,
    StaticScanResult scanResult,
    ValidationExtractionResult validationResult,
    FactGraphBuildResult factGraphs
) {

    public ScanAnalysisContext {
        Objects.requireNonNull(repositorySource, "repositorySource");
        Objects.requireNonNull(scanResult, "scanResult");
        Objects.requireNonNull(validationResult, "validationResult");
        Objects.requireNonNull(factGraphs, "factGraphs");
    }

    public List<IngestionWarning> warnings() {
        List<IngestionWarning> warnings = new ArrayList<>(scanResult.warnings());
        warnings.addAll(validationResult.warnings());
        return List.copyOf(warnings);
    }
}
