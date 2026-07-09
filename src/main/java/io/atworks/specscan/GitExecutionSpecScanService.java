package io.atworks.specscan;

import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
import io.atworks.specscan.ingestion.adapter.GitRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.adapter.TempWorkspacePreparerAdapter;
import io.atworks.specscan.ingestion.application.RepositoryIngestionService;
import io.atworks.specscan.ingestion.domain.RepositoryRequest;
import io.atworks.specscan.ingestion.domain.RepositorySource;

public class GitExecutionSpecScanService {

    public String scan(
        String repositoryUrl,
        String revisionType,
        String revision
    ) throws Exception {
        RepositoryIngestionService ingestionService = new RepositoryIngestionService(
            new GitRepositoryFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        RepositorySource repositorySource = null;
        try {
            repositorySource = ingestionService.ingest(toRepositoryRequest(repositoryUrl, revisionType, revision));

            SpringStaticScanService scanService = new SpringStaticScanService();
            StaticScanResult scanResult = scanService.scan(repositorySource);

            ValidationExtractionService extractionService = new ValidationExtractionService();
            ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);

            NormalizationService normalizationService = new NormalizationService();
            NormalizedResult normalizedResult = normalizationService.normalize(
                extractionResult.candidates(),
                scanResult.endpoints()
            );

            ExecutionSpecExporter executionSpecExporter = new ExecutionSpecExporter();
            return executionSpecExporter.export(
                scanResult,
                extractionResult.directConditions(),
                normalizedResult.conditions(),
                repositorySource
            );
        } finally {
            if (repositorySource != null) {
                new TempWorkspacePreparerAdapter().clean(repositorySource.workspaceContext());
            }
        }
    }

    private RepositoryRequest toRepositoryRequest(String repositoryUrl, String revisionType, String revision) {
        String branch = null;
        String tag = null;
        String commit = null;

        if ("branch".equalsIgnoreCase(revisionType)) {
            branch = revision;
        } else if ("tag".equalsIgnoreCase(revisionType)) {
            tag = revision;
        } else if ("commit".equalsIgnoreCase(revisionType)) {
            commit = revision;
        }
        return new RepositoryRequest(repositoryUrl, branch, tag, commit);
    }
}
