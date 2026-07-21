package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import java.util.Objects;

/**
 * Builds the shared static-analysis context exactly once for a repository workspace.
 */
public final class ScanPreparationService {

    public ScanAnalysisContext prepare(RepositorySource repositorySource) {
        Objects.requireNonNull(repositorySource, "repositorySource");

        var scanResult = new SpringStaticScanService().scan(repositorySource);
        var factGraphs = new DefaultFactCodeGraphBuilder().build(
            scanResult, repositorySource, FactGraphTraversalBudget.defaults());
        var validationResult = new ValidationExtractionService().extract(
            scanResult, repositorySource);

        return new ScanAnalysisContext(
            repositorySource, scanResult, validationResult, factGraphs);
    }
}
