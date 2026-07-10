package io.atworks.specscan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
import io.atworks.specscan.analysis.support.ValidationEvidenceGraphBuilder;
import io.atworks.specscan.ingestion.adapter.GitRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.adapter.TempWorkspacePreparerAdapter;
import io.atworks.specscan.ingestion.application.RepositoryIngestionService;
import io.atworks.specscan.ingestion.domain.RepositoryRequest;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class GitExecutionSpecScanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractionResult, repositorySource);

            NormalizationService normalizationService = new NormalizationService();
            NormalizedResult normalizedResult = normalizationService.normalize(
                extractionResult.candidates(),
                scanResult.endpoints(),
                graph
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

    public String scanWithArtifacts(
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

            Path outputPath = Path.of(repositorySource.workspaceContext().workspacePath(), "openapi.yaml");
            new OpenApiAssemblyService().assemble(scanResult, extractionResult, repositorySource, outputPath);

            JsonNode executionModel = OBJECT_MAPPER.readTree(
                Files.readString(outputPath.getParent().resolve("api-execution-model.json"))
            );
            JsonNode evidenceGraph = OBJECT_MAPPER.readTree(
                Files.readString(outputPath.getParent().resolve("validation-evidence-graph.json"))
            );

            Map<String, Object> response = new LinkedHashMap<>();
            if (executionModel.isObject()) {
                executionModel.fields().forEachRemaining(entry -> response.put(entry.getKey(), entry.getValue()));
            }
            response.put("validationEvidenceGraph", evidenceGraph);
            return OBJECT_MAPPER.writeValueAsString(response);
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
