package io.atworks.specscan;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.support.GitSpecPayloadBuilder;
import io.atworks.specscan.ingestion.adapter.GitRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.adapter.TempWorkspacePreparerAdapter;
import io.atworks.specscan.ingestion.application.RepositoryIngestionService;
import io.atworks.specscan.ingestion.domain.RepositoryRequest;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class GitSpecScanMain {

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);

        String projectName = require(options, "projectName");
        String repositoryUrl = require(options, "repositoryUrl");
        String baseUrl = require(options, "baseUrl");
        String revisionType = options.get("revisionType");
        String revision = options.get("revision");

        RepositoryIngestionService ingestionService = new RepositoryIngestionService(
            new GitRepositoryFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        RepositorySource repositorySource = null;
        try {
            RepositoryRequest request = toRepositoryRequest(repositoryUrl, revisionType, revision);
            repositorySource = ingestionService.ingest(request);

            SpringStaticScanService scanService = new SpringStaticScanService();
            StaticScanResult scanResult = scanService.scan(repositorySource);

            ValidationExtractionService extractionService = new ValidationExtractionService();
            ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);

            NormalizationService normalizationService = new NormalizationService();
            NormalizedResult normalizedResult = normalizationService.normalize(
                extractionResult.candidates(),
                scanResult.endpoints()
            );

            GitSpecPayloadBuilder payloadBuilder = new GitSpecPayloadBuilder();
            Map<String, Object> payload = payloadBuilder.build(
                projectName,
                baseUrl,
                repositorySource,
                scanResult,
                extractionResult.directConditions(),
                normalizedResult.conditions()
            );

            ObjectMapper objectMapper = new ObjectMapper();
            System.out.println(objectMapper.writeValueAsString(payload));
        } finally {
            if (repositorySource != null) {
                new TempWorkspacePreparerAdapter().clean(repositorySource.workspaceContext());
            }
        }
    }

    private static RepositoryRequest toRepositoryRequest(String repositoryUrl, String revisionType, String revision) {
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

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int separatorIndex = arg.indexOf('=');
            if (separatorIndex < 0) {
                continue;
            }
            options.put(arg.substring(2, separatorIndex), arg.substring(separatorIndex + 1));
        }
        return options;
    }

    private static String require(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + name);
        }
        return value;
    }
}
