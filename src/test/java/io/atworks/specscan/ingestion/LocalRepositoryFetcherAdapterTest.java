package io.atworks.specscan.ingestion;

import io.atworks.specscan.ingestion.adapter.LocalRepositoryFetcherAdapter;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalRepositoryFetcherAdapterTest {
    @Test
    void copiesSourceFilesAndSkipsGeneratedDirectories(@TempDir Path source, @TempDir Path target) throws Exception {
        Files.createDirectories(source.resolve("src/main/java"));
        Files.writeString(source.resolve("src/main/java/App.java"), "class App {}");
        Files.createDirectories(source.resolve("build/classes"));
        Files.writeString(source.resolve("build/classes/App.class"), "generated");
        RepositoryIdentity identity = new RepositoryIdentity(
            "local", source.getParent().toString(), source.getFileName().toString(), source.toString(), null);

        new LocalRepositoryFetcherAdapter().fetch(identity, target);

        assertThat(target.resolve("src/main/java/App.java")).exists();
        assertThat(target.resolve("build")).doesNotExist();
    }
}
