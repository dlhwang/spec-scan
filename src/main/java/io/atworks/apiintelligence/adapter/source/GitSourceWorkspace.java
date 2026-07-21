package io.atworks.apiintelligence.adapter.source;

import io.atworks.apiintelligence.domain.source.GitSource;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public final class GitSourceWorkspace {

    public SourceWorkspace acquire(GitSource source) {
        Path directory;
        try {
            directory = Files.createTempDirectory("api-intelligence-");
        } catch (IOException e) {
            throw new SourceWorkspaceException("GIT_CLONE_FAILED",
                "Unable to create clone directory", e);
        }
        try {
            Git repository = Git.cloneRepository().setURI(source.url().toString())
                .setDirectory(directory.toFile()).call();
            if (source.revision() != null) {
                repository.checkout().setName(source.revision()).call();
            }
            String revision = repository.getRepository().resolve("HEAD").name();
            repository.close();
            return new SourceWorkspace(directory, DefaultSourceWorkspaceAdapter.roots(directory),
                revision, true);
        } catch (Exception e) {
            deleteQuietly(directory);
            if (e instanceof GitAPIException) {
                throw new SourceWorkspaceException("GIT_REVISION_NOT_FOUND",
                    "Git clone or revision failed", e);
            }
            throw new SourceWorkspaceException("GIT_CLONE_FAILED", "Git clone failed", e);
        }
    }

    private static void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
