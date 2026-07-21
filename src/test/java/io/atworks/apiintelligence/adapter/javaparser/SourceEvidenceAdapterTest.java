package io.atworks.apiintelligence.adapter.javaparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atworks.apiintelligence.domain.diagnostic.Diagnostic;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.graph.CodeNode;
import io.atworks.apiintelligence.domain.graph.CodeNodeKind;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceEvidenceAdapterTest {

    @TempDir
    Path temp;

    @Test
    void readsExactLineRange() {
        Path f = temp.resolve("A.java");
        assertThatCode(() -> Files.writeString(f, "one\ntwo\nthree\n",
            StandardCharsets.UTF_8)).doesNotThrowAnyException();
        SourceLocation l = new SourceLocation("A.java", 2, 1, 2, 3);
        CodeNode n = new CodeNode("n", CodeNodeKind.CONDITION, "two", l, Map.of());
        var e = new SourceEvidenceAdapter().collect(
            new CodeGraph("api", List.of(n), List.of(), List.<Diagnostic>of()),
            new SourceWorkspace(temp, List.of(temp), null, false));
        assertThat(e).singleElement().satisfies(v -> assertThat(v.snippet()).isEqualTo("two"));
    }
}
