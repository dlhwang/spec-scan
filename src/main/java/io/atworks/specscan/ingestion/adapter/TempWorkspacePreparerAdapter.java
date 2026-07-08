package io.atworks.specscan.ingestion.adapter;

import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;
import io.atworks.specscan.ingestion.port.WorkspacePreparerPort;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;

public class TempWorkspacePreparerAdapter implements WorkspacePreparerPort {

    @Override
    public WorkspaceContext prepare() throws IngestionException {
        try {
            String executionId = UUID.randomUUID().toString();
            Path tempDir = Files.createTempDirectory("auto-oas-ws-" + executionId);
            
            // PoC 기본 정책: 캐시 재사용 비활성화
            String cacheKey = "git-cache-" + executionId; 
            boolean cacheEligible = false;

            return new WorkspaceContext(
                executionId,
                tempDir.toAbsolutePath().toString(),
                Instant.now(),
                cacheKey,
                cacheEligible
            );
        } catch (IOException e) {
            throw new IngestionException(
                IngestionErrorCode.WORKSPACE_CREATE_FAILED,
                "Failed to create temporary workspace directory",
                e
            );
        }
    }

    @Override
    public void clean(WorkspaceContext context) {
        if (context == null || context.workspacePath() == null) {
            return;
        }
        Path path = Paths.get(context.workspacePath());
        if (!Files.exists(path)) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // non-fatal warning
            System.err.println("Warning: Failed to cleanly delete workspace " + context.workspacePath() + ": " + e.getMessage());
        }
    }
}
