package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseInvariantPropagationTest {
    @TempDir Path workspace;

    @Test void propagatesOnlyGuardedResponseConstructorField() throws Exception {
        write("sample/controller/ResponseController.java", """
            package sample.controller;
            import sample.dto.PropertyResponse;
            public class ResponseController {
                public PropertyResponse get(String propertyType, String nullableNote) {
                    return new PropertyResponse(propertyType, nullableNote);
                }
                public PropertyResponse maybe(boolean missing, String propertyType) {
                    if (missing) return null;
                    return new PropertyResponse(propertyType, null);
                }
            }
            """);
        write("sample/dto/PropertyResponse.java", """
            package sample.dto;
            public class PropertyResponse {
                public PropertyResponse(String propertyType, String nullableNote) {
                    if (propertyType == null) throw new IllegalArgumentException();
                }
            }
            """);
        SourceTrace trace = new SourceTrace("src/main/java/sample/controller/ResponseController.java", 1, 10);
        ApiEndpoint endpoint = new ApiEndpoint("GET", "/property", "sample.controller.ResponseController", "get",
            List.of(), new ResponseBinding("sample.dto.PropertyResponse", trace), trace);
        ApiEndpoint maybe = new ApiEndpoint("GET", "/property/maybe",
            "sample.controller.ResponseController", "maybe", List.of(),
            new ResponseBinding("sample.dto.PropertyResponse", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint, maybe), 2, List.of(), null);
        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(scan, source(),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        EndpointRuleOutput output = TestRuleOutputs.generate(scan, source()).get("GET /property");

        assertThat(output.responseAssertions()).withFailMessage("output=%s nodes=%s edges=%s", output,
            graph.nodes(), graph.edges()).filteredOn(value -> value.targetLocation().equals("BODY"))
            .extracting(ExecutableCondition::targetPath, ExecutableCondition::operator)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("$.propertyType", "NOT_NULL"));
        assertThat(output.responseAssertions()).allSatisfy(value -> assertThat(value.evidence()).isNotEmpty());
        assertThat(TestRuleOutputs.generate(scan, source()).get("GET /property/maybe")
            .responseAssertions()).isEmpty();
    }

    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private RepositorySource source() {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 2, 2, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(2, 1, 1, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 2, "LOCAL", "main", 0));
    }
}
