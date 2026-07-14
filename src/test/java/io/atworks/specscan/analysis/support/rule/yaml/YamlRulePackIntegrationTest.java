package io.atworks.specscan.analysis.support.rule.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.EvidenceMapper;
import io.atworks.specscan.analysis.support.output.CandidateToOutputAdapter;
import io.atworks.specscan.analysis.support.rule.DefaultGraphRuleEngine;
import io.atworks.specscan.analysis.support.rule.pack.InitialRulePacks;
import java.util.*;
import org.junit.jupiter.api.Test;

class YamlRulePackIntegrationTest {
    @Test
    void executesUserRuleWithBuiltInsAndProducesObservableResult() {
        Fixture fixture = fixture();
        ConfiguredRulePacks configured = new YamlRulePackComposer().compose(InitialRulePacks.all(), yaml("PROJECT_POLICY"));
        DefaultGraphRuleEngine engine = new DefaultGraphRuleEngine((graph, scope) -> List.of(fixture.predicate()), configured.packs());

        GraphRuleEngineResult result = engine.evaluate(fixture.graph(), new MethodScope("graph", Set.of("root")));

        assertThat(configured.loadedUserRuleCount()).isOne();
        assertThat(configured.diagnostics()).isEmpty();
        assertThat(result.candidates().businessRules()).singleElement().satisfies(candidate -> {
            assertThat(candidate.ruleId()).isEqualTo("PROJECT_POLICY");
            assertThat(candidate.constraint().kind()).isEqualTo(ConstraintKind.CONTROL_FLOW_ONLY);
            assertThat(candidate.constraint().targetPath()).isNull();
            assertThat(candidate.constraint().operator()).isNull();
            assertThat(candidate.constraint().expectedValues()).isEmpty();
            assertThat(candidate.evidence()).extracting(EvidenceRef::nodeId).contains("condition", "call", "throw");
        });
        assertThat(result.report().registeredRules()).isGreaterThan(1);
        assertThat(result.report().ruleMetrics()).filteredOn(metric -> metric.ruleId().equals("PROJECT_POLICY"))
            .singleElement().satisfies(metric -> {
                assertThat(metric.evaluationCount()).isOne();
                assertThat(metric.matchCount()).isOne();
                assertThat(metric.failureCount()).isZero();
            });

        EndpointRuleOutput output = new CandidateToOutputAdapter().adapt(
            new ApiEndpoint("POST", "/projects", "ProjectController", "create", List.of(), null, null),
            result.candidates().businessRules());
        assertThat(output.excludedBusinessRules()).singleElement().satisfies(rule -> {
            assertThat(rule.ruleId()).isEqualTo("PROJECT_POLICY");
            assertThat(rule.evidence()).extracting(EvidenceRef::nodeId).contains("call");
        });
    }

    @Test
    void rejectsUserIdConflictWithoutChangingBuiltInPacks() {
        String builtInId = InitialRulePacks.all().get(0).rules().get(0).id();

        ConfiguredRulePacks configured = new YamlRulePackComposer().compose(InitialRulePacks.all(), yaml(builtInId));

        assertThat(configured.loadedUserRuleCount()).isZero();
        assertThat(configured.packs()).hasSameSizeAs(InitialRulePacks.all());
        assertThat(configured.diagnostics()).extracting(YamlRuleDiagnostic::code).containsExactly("DUPLICATE_RULE_ID");
    }

    private static String yaml(String id) {
        return """
            rules:
              - id: %s
                match:
                  predicateType: BOOLEAN_CALL
                  methodName: validateProjectPolicy
                  resolvedSignatureContains: ProjectPolicy.validateProjectPolicy
                  failureOutcome: THEN
                output:
                  category: INVARIANT
                  constraint:
                    kind: CONTROL_FLOW_ONLY
            """.formatted(id);
    }

    private static Fixture fixture() {
        SourceRange range = new SourceRange("src/ProjectValidator.java", 10, 5, 12, 6);
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range, "create()",
            TypeResolution.resolvedSignature("sample.ProjectController.create()"),
            new FactNodePayload.MethodPayload("sample.ProjectController", "create()", true));
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range,
            "!projectPolicy.validateProjectPolicy(project)", TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("UnaryExpr", "!"));
        FactNode call = new FactNode("call", FactNodeType.METHOD_CALL, range,
            "projectPolicy.validateProjectPolicy(project)",
            TypeResolution.resolvedSignature("sample.ProjectPolicy.validateProjectPolicy(sample.Project)"),
            new FactNodePayload.MethodCallPayload("validateProjectPolicy", 1, false));
        FactNode outcome = new FactNode("throw", FactNodeType.THROW, range, "throw invalidProject",
            TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("THROW", "ThrowStmt"));
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, condition, call, outcome), List.of(
            new FactEdge("control", root.id(), condition.id(), FactEdgeType.CONTROLS, -1, "IF"),
            new FactEdge("operand", condition.id(), call.id(), FactEdgeType.OPERAND_OF, 0, "EXPRESSION"),
            new FactEdge("failure", condition.id(), outcome.id(), FactEdgeType.THEN_OUTCOME, -1, "THROW")));
        EvidenceMapper mapper = new EvidenceMapper();
        PredicateCandidate predicate = new PredicateCandidate("predicate:condition", graph.graphId(), condition.id(),
            PredicateType.BOOLEAN_CALL, ExtractionStatus.EXTRACTED,
            List.of(mapper.fromFact(condition, EvidenceRole.PREDICATE),
                mapper.fromFact(outcome, EvidenceRole.FAILURE_OUTCOME)), List.of());
        return new Fixture(graph, predicate);
    }

    private record Fixture(FactCodeGraph graph, PredicateCandidate predicate) {}
}
