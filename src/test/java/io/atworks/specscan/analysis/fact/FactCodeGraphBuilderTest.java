package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FactCodeGraphBuilderTest {
    @TempDir Path workspace;

    @Test void extractsConditionCallArgumentsLocalVariableAndFailureBranch() throws Exception {
        write("demo/controller/AuthController.java", """
            package demo.controller;
            import demo.service.AuthService;
            public class AuthController { private AuthService service; public void login(String raw) { service.login(raw); } }
            """);
        write("demo/service/AuthService.java", """
            package demo.service;
            public class AuthService { private Encoder encoder; public void login(String raw) { String supplied = raw; if (!encoder.matches(supplied, "stored")) { throw new IllegalArgumentException(); } } }
            class Encoder { boolean matches(String a, String b) { return true; } }
            """);

        FactGraphBuildResult result = new DefaultFactCodeGraphBuilder().build(scan("demo.controller.AuthController", "login"), source(2), FactGraphTraversalBudget.defaults());

        assertThat(result.graphs()).hasSize(1);
        FactCodeGraph graph = result.graphs().get(0);
        assertThat(graph.nodes()).extracting(FactNode::type).contains(FactNodeType.API_METHOD, FactNodeType.METHOD, FactNodeType.CONDITION, FactNodeType.METHOD_CALL, FactNodeType.LOCAL_VARIABLE, FactNodeType.THROW);
        assertThat(graph.edges()).extracting(FactEdge::type).contains(FactEdgeType.CALLS, FactEdgeType.CONTROLS, FactEdgeType.OPERAND_OF, FactEdgeType.THEN_OUTCOME);
        assertThat(graph.edges()).extracting(FactEdge::type).contains(FactEdgeType.ASSIGNED_FROM);
        assertThat(graph.edges()).allSatisfy(edge -> assertThat(edge.sourceNodeId()).isNotEqualTo(edge.targetNodeId()));
        assertThat(graph.nodes()).filteredOn(n -> n.type() == FactNodeType.METHOD_CALL && n.snippet().contains("matches")).singleElement().satisfies(n -> assertThat(n.typeResolution().status()).isIn(TypeResolutionStatus.RESOLVED, TypeResolutionStatus.UNRESOLVED));
    }

    @Test void recordsEnumConstantAndVisitedMethodBudgetDiagnostic() throws Exception {
        write("demo/controller/FlowController.java", """
            package demo.controller;
            import demo.service.FlowService;
            public class FlowController { private FlowService service; public void proceed() { service.proceed(); } }
            """);
        write("demo/service/FlowService.java", """
            package demo.service;
            public class FlowService { enum Phase { READY, CLOSED } private Phase phase; public void proceed() { if (phase != Phase.READY) throw new IllegalStateException(); } }
            """);

        FactGraphBuildResult full = new DefaultFactCodeGraphBuilder().build(scan("demo.controller.FlowController", "proceed"), source(2), FactGraphTraversalBudget.defaults());
        assertThat(full.graphs().get(0).nodes()).extracting(FactNode::type).contains(FactNodeType.ENUM_CONSTANT);

        FactGraphBuildResult limited = new DefaultFactCodeGraphBuilder().build(scan("demo.controller.FlowController", "proceed"), source(2), new FactGraphTraversalBudget(5, 1, 300));
        assertThat(limited.diagnostics()).extracting(FactGraphDiagnostic::reason).contains("MAX_VISITED_METHODS_EXCEEDED");
        assertThat(limited.diagnostics()).allSatisfy(diagnostic -> assertThat(diagnostic.truncated()).isTrue());
    }

    @Test void recursiveCallIsRecordedWithoutRevisitingMethodBody() throws Exception {
        write("demo/controller/CycleController.java", """
            package demo.controller;
            public class CycleController { public void cycle() { cycle(); } }
            """);
        FactGraphBuildResult result = new DefaultFactCodeGraphBuilder().build(scan("demo.controller.CycleController", "cycle"), source(1), FactGraphTraversalBudget.defaults());
        assertThat(result.graphs()).hasSize(1);
        assertThat(result.graphs().get(0).nodes()).filteredOn(n -> n.type() == FactNodeType.API_METHOD || n.type() == FactNodeType.METHOD).hasSize(1);
        assertThat(result.graphs().get(0).edges()).extracting(FactEdge::type).contains(FactEdgeType.CALLS);
    }

    @Test void nestedThrowBelongsOnlyToNestedCondition() throws Exception {
        write("demo/controller/NestedController.java", """
            package demo.controller;
            public class NestedController { public void check(boolean outer, boolean inner) {
                if (outer) { if (inner) { throw new IllegalStateException(); } }
            } }
            """);
        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.NestedController", "check"), source(1), FactGraphTraversalBudget.defaults()).graphs().get(0);
        List<FactNode> conditions = graph.nodes().stream().filter(n -> n.type() == FactNodeType.CONDITION).toList();
        FactNode outer = conditions.stream().filter(n -> n.snippet().equals("outer")).findFirst().orElseThrow();
        FactNode inner = conditions.stream().filter(n -> n.snippet().equals("inner")).findFirst().orElseThrow();
        assertThat(graph.edges()).filteredOn(e -> e.type() == FactEdgeType.THEN_OUTCOME)
            .extracting(FactEdge::sourceNodeId).containsExactly(inner.id()).doesNotContain(outer.id());
    }

    private void write(String relative, String content) throws Exception { Path file = workspace.resolve("src/main/java").resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, content); }
    private StaticScanResult scan(String controller, String method) { SourceTrace trace = new SourceTrace("src/main/java/" + controller.replace('.', '/') + ".java", 1, 1); ApiEndpoint endpoint = new ApiEndpoint("POST", "/login", controller, method, List.of(), new ResponseBinding("void", trace), trace); return new StaticScanResult(List.of(endpoint), 2, List.of(), null); }
    private RepositorySource source(int files) { return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"), new WorkspaceContext("exec", workspace.toString(), Instant.now(), "cache", false), List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, files, files, 1, "DETECTED")), "Gradle", new JavaInventorySummary(files, 1, 1, true, 0), List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"), new IngestionMetadata(Instant.now(), Instant.now(), files, "LOCAL", "main", 0)); }
}
