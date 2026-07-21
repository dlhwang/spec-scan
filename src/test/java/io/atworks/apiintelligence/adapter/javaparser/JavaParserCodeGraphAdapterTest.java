package io.atworks.apiintelligence.adapter.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.apiintelligence.config.ApiIntelligenceConfiguration.GraphSettings;
import io.atworks.apiintelligence.domain.api.ApiIdGenerator;
import io.atworks.apiintelligence.domain.api.DiscoveredApi;
import io.atworks.apiintelligence.domain.graph.CodeNodeKind;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserCodeGraphAdapterTest {

    @TempDir
    Path temp;

    @Test
    void capturesConditionsThrowsReturnsAndCalls() throws Exception {
        Path f = temp.resolve("A.java");
        Files.writeString(f,
            "class A { void run(){ service.go(); if (x > 0) throw new IllegalStateException(); return; } Service service; }");
        var loc = new SourceLocation("A.java", 1, 1, 1, 120);
        String id = ApiIdGenerator.generate("GET", "/a", "A", "void run()");
        var api = new DiscoveredApi(id, "GET", "/a", "A", "void run()", List.of(), "void", loc);
        var g = new JavaParserCodeGraphAdapter().build(api,
            new SourceWorkspace(temp, List.of(temp), null, false), new GraphSettings(8, 100, 2000));
        assertThat(g.nodes()).extracting(n -> n.kind())
            .contains(CodeNodeKind.CONDITION, CodeNodeKind.EXCEPTION, CodeNodeKind.METHOD);
    }
}
