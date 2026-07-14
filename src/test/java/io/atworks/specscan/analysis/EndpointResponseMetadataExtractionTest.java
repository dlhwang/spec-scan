package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.support.EndpointExtractor;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EndpointResponseMetadataExtractionTest {
    @TempDir Path workspace;

    @Test void extractsResponseEntityStatusAndLiteralHeader() throws Exception {
        Path source = workspace.resolve("src/main/java/demo/Controller.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package demo;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;
            @RestController class Controller {
              @PostMapping("/items") ResponseEntity<String> create() {
                return ResponseEntity.status(HttpStatus.CREATED).header("Location", "/items/1").body("ok");
              }
            }
            """);
        ApiEndpoint endpoint = new EndpointExtractor(workspace).extract(source).get(0);
        assertThat(endpoint.responseBinding().explicitStatus()).isEqualTo(201);
        assertThat(endpoint.responseBinding().explicitHeaders()).containsEntry("Location", "/items/1");
    }

    @Test void preservesConflictingResponseEntityStatuses() throws Exception {
        Path source = workspace.resolve("src/main/java/demo/ConflictController.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package demo;
            import org.springframework.http.*;
            import org.springframework.web.bind.annotation.*;
            @RestController class ConflictController {
              @GetMapping("/items") ResponseEntity<String> get(boolean empty) {
                if (empty) return ResponseEntity.noContent().build();
                return ResponseEntity.ok("ok");
              }
            }
            """);
        ApiEndpoint endpoint = new EndpointExtractor(workspace).extract(source).get(0);
        assertThat(endpoint.responseBinding().explicitStatus()).isNull();
        assertThat(endpoint.responseBinding().statusSource()).startsWith("CONFLICT:");
    }
}
