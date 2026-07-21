package io.atworks.apiintelligence.adapter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atworks.apiintelligence.domain.source.LocalSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceWorkspaceAdapterTest {

    @TempDir
    Path temp;
    private final DefaultSourceWorkspaceAdapter adapter = new DefaultSourceWorkspaceAdapter();

    @Test
    void acquiresLocalReadOnlyWorkspaceAndFindsJavaRoot() throws Exception {
        Path root = temp.resolve("project");
        Files.createDirectories(root.resolve("src/main/java/demo"));
        try (var workspace = adapter.acquire(new LocalSource(root))) {
            assertThat(workspace.root()).isEqualTo(root.toAbsolutePath().normalize());
            assertThat(workspace.owned()).isFalse();
            assertThat(workspace.sourceRoots()).contains(
                root.resolve("src/main/java").toAbsolutePath().normalize());
        }
        assertThat(Files.exists(root)).isTrue();
    }

    @Test
    void rejectsMissingAndUnsupportedSources() {
        assertThatThrownBy(() -> adapter.acquire(new LocalSource(temp.resolve("missing"))))
            .isInstanceOf(SourceWorkspaceException.class).hasMessageContaining("does not exist");
    }
}
