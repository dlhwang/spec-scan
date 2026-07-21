package io.atworks.apiintelligence.domain.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SourceWorkspace implements AutoCloseable {

    private final Path root;
    private final List<Path> sourceRoots;
    private final String resolvedRevision;
    private final boolean owned;

    public SourceWorkspace(Path root, List<Path> sourceRoots, String resolvedRevision,
        boolean owned) {
        this.root = root.toAbsolutePath().normalize();
        this.sourceRoots = sourceRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
        this.resolvedRevision = resolvedRevision;
        this.owned = owned;
    }

    public Path root() {
        return root;
    }

    public List<Path> sourceRoots() {
        return sourceRoots;
    }

    public String resolvedRevision() {
        return resolvedRevision;
    }

    public boolean owned() {
        return owned;
    }

    @Override
    public void close() throws IOException {
        if (owned && Files.exists(root)) {
            deleteTreeBestEffort(root);
        }
    }

    private static void deleteTreeBestEffort(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        try {
                            Files.setAttribute(p, "dos:readonly", false);
                        } catch (Exception ignored) {
                        }
                        Files.deleteIfExists(p);
                        break;
                    } catch (IOException e) {
                        if (attempt == 2) {
                            return;
                        }
                        try {
                            Thread.sleep(50L * (attempt + 1));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        } catch (IOException ignored) { /* Windows may briefly retain a JGit pack handle. */ }
    }
}
