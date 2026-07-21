package io.atworks.specscan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
import io.atworks.specscan.analysis.application.RuleOutputService;
import io.atworks.specscan.analysis.application.ScanAnalysisContext;
import io.atworks.specscan.analysis.application.ScanPreparationService;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
import io.atworks.specscan.ingestion.adapter.RepositorySourceFetcherAdapter;
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
            new RepositorySourceFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        RepositorySource repositorySource = null;
        try {
            repositorySource = ingestionService.ingest(toRepositoryRequest(repositoryUrl, revisionType, revision));

            ScanAnalysisContext context = new ScanPreparationService().prepare(repositorySource);

            Map<String, EndpointRuleOutput> ruleOutputs = new RuleOutputService().generate(context);
            return new ExecutionSpecExporter().export(context.scanResult(), ruleOutputs,
                context.warnings(), repositorySource);
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
            new RepositorySourceFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        RepositorySource repositorySource = null;
        try {
            repositorySource = ingestionService.ingest(toRepositoryRequest(repositoryUrl, revisionType, revision));

            ScanAnalysisContext context = new ScanPreparationService().prepare(repositorySource);

            Path outputPath = Path.of(repositorySource.workspaceContext().workspacePath(), "openapi.yaml");
            new OpenApiAssemblyService().assemble(context, outputPath);

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

    public String scanWithLlmArtifacts(
        String repositoryUrl,
        String revisionType,
        String revision,
        io.atworks.apiintelligence.config.ApiIntelligenceConfiguration config
    ) throws Exception {
        RepositoryIngestionService ingestionService = new RepositoryIngestionService(
            new RepositorySourceFetcherAdapter(), new TempWorkspacePreparerAdapter());
        RepositorySource repositorySource = null;
        try {
            repositorySource = ingestionService.ingest(
                toRepositoryRequest(repositoryUrl, revisionType, revision));
            ScanAnalysisContext context = new ScanPreparationService().prepare(repositorySource);
            Map<String, Object> analysis =
                new io.atworks.apiintelligence.application.ApiIntelligenceAnalysisService()
                    .analyze(context, config);
            return OBJECT_MAPPER.writeValueAsString(
                new io.atworks.apiintelligence.application.LlmScanResponseAdapter()
                    .toScanResponse(analysis));
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
