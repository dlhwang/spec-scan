package io.atworks.specscan.ingestion;

import io.atworks.specscan.ingestion.adapter.GitRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRepositoryFetcherAdapterTest {

    private GitRepositoryFetcherAdapter fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new GitRepositoryFetcherAdapter();
    }

    @Test
    void testSuccessfulCloneAndCheckout(@TempDir Path tempDir) {
        // Given
        RepositoryIdentity identity = new RepositoryIdentity(
            "github.com",
            "octocat",
            "Spoon-Knife",
            "https://github.com/octocat/Spoon-Knife.git",
            "main"
        );

        // When
        fetcher.fetch(identity, tempDir);

        // Then
        assertThat(Files.exists(tempDir.resolve("README.md"))).isTrue();
        assertThat(Files.exists(tempDir.resolve(".git"))).isTrue();
    }

    @Test
    void testCloneFailureOnInvalidRepository(@TempDir Path tempDir) {
        // Given
        RepositoryIdentity identity = new RepositoryIdentity(
            "github.com",
            "non-existent-owner",
            "non-existent-repo",
            "https://github.com/non-existent-owner/non-existent-repo.git",
            null
        );

        // When & Then
        assertThatThrownBy(() -> fetcher.fetch(identity, tempDir))
            .isInstanceOf(IngestionException.class)
            .hasFieldOrPropertyWithValue("errorCode", IngestionErrorCode.CLONE_FAILED);
    }
}
