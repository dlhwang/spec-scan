package io.atworks.specscan.analysis.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResponseFactoryConstantTest {
    @TempDir Path workspace;

    @Test void extractsSuccessAndNullConstantsFromDelegatedResponseFactory() throws Exception {
        write("sample/controller/Controller.java", """
            package sample.controller;
            import sample.ApiResponse;
            public class Controller {
                public ApiResponse<String> login() { return ApiResponse.success("token"); }
            }
            """);
        write("sample/ApiResponse.java", """
            package sample;
            public record ApiResponse<T>(Object httpStatus, boolean success, T response, Object error) {
                public static <T> ApiResponse<T> success(T response) {
                    return new ApiResponse<>("OK", true, response, null);
                }
            }
            """);
        SourceTrace trace = new SourceTrace("src/main/java/sample/controller/Controller.java", 4, 4);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/login", "sample.controller.Controller", "login",
            List.of(), new ResponseBinding("sample.ApiResponse<String>", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 2, List.of(), null);

        var build = new io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder().build(scan,
            source(), io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget.defaults());
        EndpointRuleOutput output = new io.atworks.specscan.analysis.application.RuleOutputService()
            .generate(scan, build, List.of()).get("POST /login");

        assertThat(output.responseAssertions()).withFailMessage("output=%s graph=%s", output,
            build.graphs().get(0))
            .filteredOn(assertion -> assertion.ruleId().equals("RESPONSE_FACTORY_CONSTANT"))
            .extracting("targetPath", "expectedValues")
            .contains(org.assertj.core.groups.Tuple.tuple("$.success", List.of("true")),
                org.assertj.core.groups.Tuple.tuple("$.error", List.of("null")));
    }

    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private RepositorySource source() {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 2, 2, 1,
                "DETECTED")), "Gradle", new JavaInventorySummary(2, 1, 1, true, 0), List.of(),
            List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 2, "LOCAL", "main", 0));
    }
}
