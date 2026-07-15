package io.atworks.specscan.ingestion;

import io.atworks.specscan.ingestion.application.RepositoryIngestionService;
import io.atworks.specscan.ingestion.adapter.LocalRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.domain.*;
import io.atworks.specscan.ingestion.port.RepositoryFetcherPort;
import io.atworks.specscan.ingestion.port.WorkspacePreparerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryIngestionServiceTest {

    private RepositoryIngestionService service;
    private MockRepositoryFetcher fetcher;
    private MockWorkspacePreparer preparer;

    @BeforeEach
    void setUp() {
        fetcher = new MockRepositoryFetcher();
        preparer = new MockWorkspacePreparer();
        service = new RepositoryIngestionService(fetcher, preparer);
    }

    @Test
    void testValidRequestIngestion(@TempDir Path tempDir) throws IOException {
        // Given
        preparer.setWorkspacePath(tempDir.toString());
        
        // 가짜 소스 디렉터리 및 pom.xml 구성
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        
        Path javaFile = srcDir.resolve("HelloController.java");
        Files.writeString(javaFile, """
            package com.example;
            @RestController
            @RequestMapping("/hello")
            public class HelloController {
                @GetMapping
                public String sayHello() { return "hello"; }
            }
        """);

        RepositoryRequest request = new RepositoryRequest(
            "https://github.com/test-owner/test-repo", "main", null, null
        );

        // When
        RepositorySource source = service.ingest(request);

        // Then
        assertThat(source.repositoryIdentity().owner()).isEqualTo("test-owner");
        assertThat(source.repositoryIdentity().repositoryName()).isEqualTo("test-repo");
        assertThat(source.repositoryIdentity().requestedRef()).isEqualTo("main");
        assertThat(source.buildToolHint()).isEqualTo("Maven");
        assertThat(source.javaInventorySummary().totalJavaFileCount()).isEqualTo(1);
        
        SourceRootCandidate candidate = source.sourceRoots().get(0);
        assertThat(candidate.javaFileCount()).isEqualTo(1);
        assertThat(candidate.springAnnotationCandidateCount()).isEqualTo(3); // @RestController, @RequestMapping, @GetMapping
        assertThat(candidate.selectionPriority()).isEqualTo(1);
    }

    @Test
    void testInvalidRepositoryUrl() {
        RepositoryRequest request = new RepositoryRequest("invalid-url");
        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IngestionException.class)
            .hasFieldOrPropertyWithValue("errorCode", IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE);
    }

    @Test
    void testLocalRepositoryDirectory(@TempDir Path localRepository, @TempDir Path workspace) throws IOException {
        Path sourceRoot = localRepository.resolve("src/main/java");
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("LocalController.java"), "class LocalController {}");
        preparer.setWorkspacePath(workspace.toString());
        RepositoryIngestionService localService = new RepositoryIngestionService(
            new LocalRepositoryFetcherAdapter(), preparer);

        RepositorySource source = localService.ingest(new RepositoryRequest(localRepository.toString()));

        assertThat(source.repositoryIdentity().host()).isEqualTo("local");
        assertThat(source.repositoryIdentity().normalizedCloneUrl()).isEqualTo(localRepository.toAbsolutePath().toString());
        assertThat(source.javaInventorySummary().totalJavaFileCount()).isEqualTo(1);
    }

    @Test
    void testLocalRepositoryRejectsRevision(@TempDir Path localRepository) {
        RepositoryRequest request = new RepositoryRequest(localRepository.toString(), "main", null, null);

        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IngestionException.class)
            .hasFieldOrPropertyWithValue("errorCode", IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE);
    }

    @Test
    void testUnsupportedHosts() {
        RepositoryRequest request = new RepositoryRequest("https://gitlab.com/owner/repo");
        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IngestionException.class)
            .hasFieldOrPropertyWithValue("errorCode", IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE);
    }

    @Test
    void testSshRepositoryUrl() {
        RepositoryRequest request = new RepositoryRequest("git@github.com:owner/repo.git");
        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IngestionException.class)
            .hasFieldOrPropertyWithValue("errorCode", IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE);
    }

    @Test
    void testArchiveRepositoryUrl() {
        RepositoryRequest request = new RepositoryRequest("https://github.com/owner/repo/archive/refs/heads/main.zip");
        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IngestionException.class)
            .hasFieldOrPropertyWithValue("errorCode", IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE);
    }

    @Test
    void testIngestionCleanupOnFetchFailure() {
        // Given
        preparer.setWorkspacePath("dummy-path");
        fetcher.setShouldFail(true);

        RepositoryRequest request = new RepositoryRequest("https://github.com/owner/repo");

        // When & Then
        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IngestionException.class);
        
        assertThat(preparer.isCleanCalled()).isTrue();
    }

    // --- Mock Classes ---

    private static class MockRepositoryFetcher implements RepositoryFetcherPort {
        private boolean shouldFail = false;

        public void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public void fetch(RepositoryIdentity identity, Path targetPath) throws IngestionException {
            if (shouldFail) {
                throw new IngestionException(IngestionErrorCode.CLONE_FAILED, "Mock clone failure");
            }
        }
    }

    private static class MockWorkspacePreparer implements WorkspacePreparerPort {
        private String workspacePath;
        private final AtomicBoolean cleanCalled = new AtomicBoolean(false);

        public void setWorkspacePath(String workspacePath) {
            this.workspacePath = workspacePath;
        }

        public boolean isCleanCalled() {
            return cleanCalled.get();
        }

        @Override
        public WorkspaceContext prepare() throws IngestionException {
            return new WorkspaceContext(
                "mock-execution-id",
                workspacePath != null ? workspacePath : "mock-path",
                Instant.now(),
                "mock-cache-key",
                false
            );
        }

        @Override
        public void clean(WorkspaceContext context) {
            cleanCalled.set(true);
        }
    }
}
