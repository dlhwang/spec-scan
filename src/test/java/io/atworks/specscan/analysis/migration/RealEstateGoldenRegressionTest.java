package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.application.*;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.rule.*;
import io.atworks.specscan.analysis.support.rule.pack.InitialRulePacks;
import io.atworks.specscan.analysis.domain.rule.MethodScope;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REALESTATE_WORKSPACE", matches = ".+")
class RealEstateGoldenRegressionTest {
    private static final Set<String> OPERATORS = Set.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE",
        "CONTAINS", "NOT_CONTAINS", "EMPTY", "NOT_EMPTY", "NULL", "NOT_NULL");

    @Test void sixEndpointsRemainGraphBackedCanonicalAndDeterministic() throws Exception {
        Path workspace = Path.of(System.getenv("REALESTATE_WORKSPACE"));
        RepositorySource source = source(workspace);
        StaticScanResult scan = new SpringStaticScanService().scan(source);
        assertThat(scan.endpoints()).extracting(ApiEndpoint::controllerMethod)
            .containsExactlyInAnyOrder("login", "getProperties", "getProperty", "post", "put", "remove");
        FactCodeGraph postGraph = new DefaultFactCodeGraphBuilder().build(scan, source,
            FactGraphTraversalBudget.defaults()).graphs().stream().filter(graph -> graph.nodes().stream()
                .anyMatch(node -> node.id().equals(graph.apiMethodNodeId())
                    && node.payload() instanceof FactNodePayload.MethodPayload method
                    && method.declarationSignature().startsWith("post("))).findFirst().orElseThrow();
        assertThat(postGraph.nodes()).withFailMessage("postGraph=%s", postGraph)
            .extracting(FactNode::type).contains(FactNodeType.CONDITION);
        Set<String> methods = new LinkedHashSet<>();
        postGraph.nodes().stream().filter(node -> node.type() == FactNodeType.API_METHOD
            || node.type() == FactNodeType.METHOD || node.type() == FactNodeType.CONSTRUCTOR)
            .map(FactNode::id).forEach(methods::add);
        var postCandidates = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all()).evaluate(postGraph, new MethodScope(postGraph.graphId(), methods))
            .candidates().businessRules();
        assertThat(postCandidates).withFailMessage("candidates=%s", postCandidates)
            .filteredOn(candidate -> candidate.ruleId() != null && (candidate.ruleId().contains("NULL_REJECTION")
                || candidate.ruleId().contains("EMPTY_REJECTION")))
            .anySatisfy(candidate -> assertThat(candidate.targetStatus().name()).isEqualTo("RESOLVED"));
        RuleOutputMigrationService migration = new RuleOutputMigrationService();
        RuleOutputMigrationResult first = migration.migrate(scan, source, List.of());
        RuleOutputMigrationResult second = migration.migrate(scan, source, List.of());
        assertThat(first.outputs()).isEqualTo(second.outputs());
        assertThat(first.outputs()).hasSize(6);

        first.outputs().values().forEach(output -> {
            List<ExecutableCondition> executable = new ArrayList<>(output.requestPreconditions());
            executable.addAll(output.responseAssertions());
            assertThat(executable).allSatisfy(condition -> {
                assertThat(condition.operator()).isIn(OPERATORS);
                assertThat(condition.evidence()).isNotEmpty();
                assertThat(condition.evidence()).allSatisfy(ref -> {
                    assertThat(ref.nodeId()).isNotBlank();
                    assertThat(ref.filePath()).isNotBlank();
                });
            });
            assertThat(output.excludedBusinessRules()).allSatisfy(rule -> {
                assertThat(rule.ruleId()).isIn("SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW",
                    "JDK_OPTIONAL_LOOKUP_FAILURE", "SPRING_SECURITY_PASSWORD_MATCH_FAILURE",
                    "JAVA_AUTHORIZATION_GUARD_CALL");
                assertThat(rule.evidence()).extracting(EvidenceRef::role)
                    .contains(EvidenceRole.PREDICATE, EvidenceRole.FAILURE_OUTCOME);
            });
        });

        EndpointRuleOutput post = first.outputs().get("POST /api/estate/properties");
        assertThat(post.requestPreconditions()).withFailMessage("outputs=%s", first.outputs())
            .extracting(ExecutableCondition::targetPath,
            ExecutableCondition::operator).contains(org.assertj.core.groups.Tuple.tuple("$", "NOT_NULL"),
                org.assertj.core.groups.Tuple.tuple("$.propertyType", "NOT_NULL"),
                org.assertj.core.groups.Tuple.tuple("$.contractDetails", "NOT_EMPTY"));
        EndpointRuleOutput remove = first.outputs().get("DELETE /api/estate/properties/{propertyId}");
        assertThat(remove.responseAssertions()).extracting(ExecutableCondition::targetPath,
            ExecutableCondition::operator, ExecutableCondition::expectedValues)
            .contains(org.assertj.core.groups.Tuple.tuple("$status", "EQ", List.of("204")),
                org.assertj.core.groups.Tuple.tuple("$body", "EMPTY", List.of()));
        assertThat(first.outputs().get("POST /api/auth/login").responseAssertions())
            .noneSatisfy(assertion -> assertThat(assertion.targetPath()).isEqualTo("$.result"));
        for (String key : List.of("GET /api/estate/properties/{propertyId}",
                "DELETE /api/estate/properties/{propertyId}"))
            assertThat(first.outputs().get(key).requestPreconditions())
                .extracting(ExecutableCondition::operator).doesNotContain("NOT_EMPTY");
    }

    private RepositorySource source(Path workspace) {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("local", "dlhwang", "RealEstate",
            workspace.toString(), "main"), new WorkspaceContext("golden", workspace.toString(), now,
            "local", false), List.of(new SourceRootCandidate("main", "src/main/java", "Gradle", true,
            1, 1, 1, "DETECTED")), "Gradle", new JavaInventorySummary(1, 1, 1, true, 0),
            List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 1, "LOCAL", "main", 0));
    }
}
