package io.atworks.specscan.ingestion.adapter;

import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.port.RepositoryFetcherPort;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

public final class LocalRepositoryFetcherAdapter implements RepositoryFetcherPort {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
        ".git", ".gradle", ".idea", ".settings", "build", "target", "out", "bin", "node_modules");

    @Override
    public void fetch(RepositoryIdentity identity, Path targetPath) throws IngestionException {
        Path sourcePath = Path.of(identity.normalizedCloneUrl()).toAbsolutePath().normalize();
        try {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    if (!directory.equals(sourcePath)
                        && EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(targetPath.resolve(sourcePath.relativize(directory).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (!attributes.isSymbolicLink()) {
                        Files.copy(file, targetPath.resolve(sourcePath.relativize(file).toString()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IngestionException(
                IngestionErrorCode.CLONE_FAILED,
                "Failed to copy local repository: " + sourcePath,
                exception
            );
        }
    }
}
