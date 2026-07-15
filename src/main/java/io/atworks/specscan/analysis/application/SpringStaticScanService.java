package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.support.EndpointExtractor;
import io.atworks.specscan.ingestion.domain.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SpringStaticScanService {

    static {
        com.github.javaparser.StaticJavaParser.getConfiguration()
            .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    public StaticScanResult scan(RepositorySource repositorySource) throws IngestionException {
        Instant startedAt = Instant.now();
        List<ApiEndpoint> endpoints = new ArrayList<>();
        List<IngestionWarning> warnings = new ArrayList<>();
        int[] scannedClassesCount = {0};

        WorkspaceContext workspace = repositorySource.workspaceContext();
        if (workspace == null || workspace.workspacePath() == null) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Workspace context or path is not available for scanning"
            );
        }

        Path workspacePath = Paths.get(workspace.workspacePath());
        if (!Files.exists(workspacePath)) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Workspace directory does not exist: " + workspacePath
            );
        }

        EndpointExtractor extractor = new EndpointExtractor(workspacePath);

        // Scan active source roots (e.g. src/main/java)
        for (SourceRootCandidate srcRoot : repositorySource.sourceRoots()) {
            if (!srcRoot.status().equals("DETECTED")) {
                continue;
            }

            Path srcRootPath = workspacePath.resolve(srcRoot.rootPath());
            if (!Files.exists(srcRootPath)) {
                continue;
            }

            try (Stream<Path> fileStream = Files.walk(srcRootPath)) {
                fileStream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        scannedClassesCount[0]++;
                        try {
                            List<ApiEndpoint> extracted = extractor.extract(file);
                            endpoints.addAll(extracted);
                        } catch (Exception e) {
                            String relPath = workspacePath.relativize(file).toString().replace("\\", "/");
                            warnings.add(new IngestionWarning(
                                "PARSING_FAILED",
                                "Failed to parse java file during static scan: " + e.getMessage(),
                                relPath,
                                "LOW"
                            ));
                        }
                    });
            } catch (IOException e) {
                warnings.add(new IngestionWarning(
                    "ROOT_SCAN_FAILED",
                    "Failed to walk source root directory: " + e.getMessage(),
                    srcRoot.rootPath(),
                    "MEDIUM"
                ));
            }
        }

        Instant completedAt = Instant.now();
        IngestionMetadata metadata = new IngestionMetadata(
            startedAt,
            completedAt,
            Duration.between(startedAt, completedAt).toMillis(),
            repositorySource.ingestionMetadata().requestedRefType(),
            repositorySource.ingestionMetadata().resolvedRef(),
            warnings.size()
        );

        return new StaticScanResult(
            endpoints,
            scannedClassesCount[0],
            warnings,
            metadata
        );
    }
}
