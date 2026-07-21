package io.atworks.apiintelligence.adapter.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonRunArtifactAdapterTest {

    @TempDir
    Path temp;

    @Test
    void writesPrettyJsonInsideRun() {
        var a = new JsonRunArtifactAdapter();
        Path run = a.createRun(temp, "run-1");
        a.writeJson(run, "api/a/result.json", Map.of("ok", true));
        assertThat(run.resolve("api/a/result.json")).exists().content(StandardCharsets.UTF_8)
            .contains("\"ok\" : true");
    }

    @Test
    void rejectsTraversal() {
        var a = new JsonRunArtifactAdapter();
        Path run = a.createRun(temp, "run-2");
        assertThatThrownBy(() -> a.writeJson(run, "../escape.json", Map.of())).isInstanceOf(
            IllegalArgumentException.class);
    }
}
