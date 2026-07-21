package io.atworks.apiintelligence.adapter.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserApiDiscoveryAdapterTest {

    @TempDir
    Path temp;

    @Test
    void discoversClassAndMethodMappings() throws Exception {
        Path source = temp.resolve("src/main/java/demo/OrderController.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package demo;
            @RestController @RequestMapping(\"/orders\")
            class OrderController {
              @GetMapping(\"/{id}\") public Order find(@PathVariable String id) { return null; }
              @PostMapping public Order create(@RequestBody Order input) { return null; }
            }
            """, StandardCharsets.UTF_8);
        SourceWorkspace workspace = new SourceWorkspace(temp,
            List.of(temp.resolve("src/main/java")), null, false);
        var apis = new JavaParserApiDiscoveryAdapter().discover(workspace);
        assertThat(apis).hasSize(2);
        assertThat(apis).extracting(a -> a.httpMethod() + " " + a.path())
            .containsExactly("GET /orders/{id}", "POST /orders");
        assertThat(apis.get(0).controllerType()).isEqualTo("demo.OrderController");
        assertThat(apis.get(0).sourceLocation().relativePath()).isEqualTo(
            "src/main/java/demo/OrderController.java");
    }
}
