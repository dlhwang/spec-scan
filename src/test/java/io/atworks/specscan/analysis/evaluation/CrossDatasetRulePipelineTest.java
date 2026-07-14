package io.atworks.specscan.analysis.evaluation;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.rule.*;
import io.atworks.specscan.analysis.support.rule.pack.*;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class CrossDatasetRulePipelineTest {
    @TempDir Path workspace;

    @Test void reusesOptionalRuleAcrossRenamedDatasetsWithoutMatchingStringMatches() throws Exception {
        write("evaluation/alpha/CatalogController.java", """
            package evaluation.alpha;
            import java.util.Optional;
            public class CatalogController {
                public String requireEntry(Optional<String> candidate) {
                    return candidate.orElseThrow(IllegalArgumentException::new);
                }
            }
            """);
        write("evaluation/beta/RegistryController.java", """
            package evaluation.beta;
            import java.util.Optional;
            public class RegistryController {
                public String obtainRecord(Optional<String> source) {
                    return source.orElseThrow(IllegalStateException::new);
                }
            }
            """);
        write("evaluation/negative/TextController.java", """
            package evaluation.negative;
            public class TextController {
                public void validate(String reference, String sample) {
                    if (!reference.matches(sample)) throw new IllegalArgumentException();
                }
            }
            """);

        List<ApiEndpoint> endpoints = List.of(
            endpoint("/catalog", "evaluation.alpha.CatalogController", "requireEntry"),
            endpoint("/registry", "evaluation.beta.RegistryController", "obtainRecord"),
            endpoint("/text", "evaluation.negative.TextController", "validate"));
        StaticScanResult scan = new StaticScanResult(endpoints, 3, List.of(), null);
        FactGraphBuildResult built = new DefaultFactCodeGraphBuilder().build(scan, source(), FactGraphTraversalBudget.defaults());
        GraphRuleEngine engine = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all());
        List<BusinessRuleCandidate> resolved = new ArrayList<>();
        for (FactCodeGraph graph : built.graphs()) {
            Set<String> methods = new LinkedHashSet<>();
            graph.nodes().stream().filter(node -> node.type() == FactNodeType.API_METHOD || node.type() == FactNodeType.METHOD)
                .forEach(node -> methods.add(node.id()));
            engine.evaluate(graph, new MethodScope(graph.graphId(), methods)).candidates().businessRules().stream()
                .filter(candidate -> candidate.semanticStatus() == SemanticStatus.RESOLVED).forEach(resolved::add);
        }

        assertThat(resolved).filteredOn(candidate -> candidate.ruleId().equals(OptionalLookupFailureRule.ID)).hasSize(2);
        assertThat(resolved).noneMatch(candidate -> candidate.ruleId().equals(PasswordEncoderMatchFailureRule.ID));
    }

    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private ApiEndpoint endpoint(String path, String owner, String method) {
        SourceTrace trace = new SourceTrace("src/main/java/" + owner.replace('.', '/') + ".java", 1, 1);
        return new ApiEndpoint("POST", path, owner, method, List.of(), new ResponseBinding("String", trace), trace);
    }
    private RepositorySource source() {
        return new RepositorySource(new RepositoryIdentity("test", "owner", "evaluation", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), Instant.now(), "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 3, 3, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(3, 3, 3, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(Instant.now(), Instant.now(), 3, "LOCAL", "main", 0));
    }
}
