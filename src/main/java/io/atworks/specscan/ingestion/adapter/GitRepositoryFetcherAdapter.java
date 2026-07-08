package io.atworks.specscan.ingestion.adapter;

import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.port.RepositoryFetcherPort;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.nio.file.Path;

public class GitRepositoryFetcherAdapter implements RepositoryFetcherPort {

    @Override
    public void fetch(RepositoryIdentity identity, Path targetPath) throws IngestionException {
        File directory = targetPath.toFile();
        
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(identity.normalizedCloneUrl())
                .setDirectory(directory)
                .setCloneAllBranches(true);

        String ref = identity.requestedRef();

        try (Git git = cloneCommand.call()) {
            if (ref != null && !ref.isBlank()) {
                git.checkout()
                   .setName(ref)
                   .call();
            }
        } catch (GitAPIException e) {
            throw new IngestionException(
                IngestionErrorCode.CLONE_FAILED,
                "Failed to clone repository: " + identity.normalizedCloneUrl() + " with ref: " + ref,
                e
            );
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.CLONE_FAILED,
                "Unexpected error during git fetch",
                e
            );
        }
    }
}
