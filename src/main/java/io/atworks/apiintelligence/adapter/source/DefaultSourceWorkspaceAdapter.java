package io.atworks.apiintelligence.adapter.source;

import io.atworks.apiintelligence.domain.source.AnalysisSource;
import io.atworks.apiintelligence.domain.source.GitSource;
import io.atworks.apiintelligence.domain.source.LocalSource;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import io.atworks.apiintelligence.port.out.SourceWorkspacePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DefaultSourceWorkspaceAdapter implements SourceWorkspacePort {

    private final GitSourceWorkspace git = new GitSourceWorkspace();

    @Override
    public SourceWorkspace acquire(AnalysisSource source) {
        if (source instanceof LocalSource local) {
            return local(local.path());
        }
        if (source instanceof GitSource remote) {
            return git.acquire(remote);
        }
        throw new SourceWorkspaceException("UNSUPPORTED_SOURCE", "Unsupported analysis source");
    }

    private SourceWorkspace local(Path path) {
        if (!Files.exists(path)) {
            throw new SourceWorkspaceException("LOCAL_PATH_NOT_FOUND",
                "Local project path does not exist");
        }
        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
            throw new SourceWorkspaceException("LOCAL_PATH_NOT_READABLE",
                "Local project path is not readable");
        }
        return new SourceWorkspace(path, roots(path), null, false);
    }

    static List<Path> roots(Path root) {
        List<Path> result = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isDirectory).filter(p -> p.getFileName().toString().equals("java"))
                .filter(
                    p -> p.toString().replace('\\', '/').endsWith("src/main/java") || p.toString()
                        .replace('\\', '/').endsWith("src/test/java"))
                .sorted(Comparator.comparing(Path::toString)).forEach(result::add);
        } catch (IOException e) {
            throw new SourceWorkspaceException("SOURCE_ROOT_SCAN_FAILED",
                "Unable to scan source roots", e);
        }
        if (result.isEmpty()) {
            result.add(root);
        }
        return List.copyOf(result);
    }
}
