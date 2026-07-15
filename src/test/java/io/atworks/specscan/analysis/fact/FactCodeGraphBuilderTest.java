package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.GraphRuleEngineResult;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.rule.*;
import io.atworks.specscan.analysis.support.rule.pack.*;
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

    @Test void repeatedBuildProducesDeterministicGraph() throws Exception {
        write("demo/controller/StableController.java", """
            package demo.controller;
            import demo.service.StableService;
            public class StableController { private StableService service;
                public void validate(String value) { service.validate(value); }
            }
            """);
        write("demo/service/StableService.java", """
            package demo.service;
            public class StableService {
                public void validate(String value) {
                    if (value == null) throw new IllegalArgumentException();
                }
            }
            """);

        StaticScanResult scan = scan("demo.controller.StableController", "validate");
        RepositorySource source = source(2);
        FactGraphBuildResult first = new DefaultFactCodeGraphBuilder().build(
            scan, source, FactGraphTraversalBudget.defaults());
        FactGraphBuildResult second = new DefaultFactCodeGraphBuilder().build(
            scan, source, FactGraphTraversalBudget.defaults());

        assertThat(second.graphs()).isEqualTo(first.graphs());
        assertThat(second.diagnostics()).isEqualTo(first.diagnostics());
    }

    @Test void objectCreationReachesSourceConstructorAndItsGuard() throws Exception {
        write("demo/controller/RangeController.java", """
            package demo.controller;
            import demo.model.LongRange;
            public class RangeController {
                public void search(Long start, Long end) { new LongRange(start, end); }
            }
            """);
        write("demo/model/LongRange.java", """
            package demo.model;
            public class LongRange {
                public LongRange(Long start, Long end) {
                    if (start > end) throw new IllegalArgumentException();
                }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.RangeController", "search"), source(2),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        assertThat(graph.nodes()).extracting(FactNode::type)
            .contains(FactNodeType.OBJECT_CREATION, FactNodeType.CONSTRUCTOR, FactNodeType.CONDITION,
                FactNodeType.THROW);
        assertThat(graph.edges()).extracting(FactEdge::type)
            .contains(FactEdgeType.CREATES, FactEdgeType.CALLS, FactEdgeType.CONTROLS,
                FactEdgeType.THEN_OUTCOME);
    }

    @Test void constructorReachesSuperConstructorGuard() throws Exception {
        write("demo/controller/RangeController.java", """
            package demo.controller;
            import demo.model.LongRange;
            public class RangeController {
                public void search(Long start, Long end) { new LongRange(start, end); }
            }
            """);
        write("demo/model/Range.java", """
            package demo.model;
            public class Range {
                public Range(Long start, Long end) {
                    if (start > end) throw new IllegalArgumentException();
                }
            }
            """);
        write("demo/model/LongRange.java", """
            package demo.model;
            public class LongRange extends Range {
                public LongRange(Long start, Long end) { super(start, end); }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.RangeController", "search"), source(3),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        assertThat(graph.nodes()).filteredOn(node -> node.type() == FactNodeType.CONSTRUCTOR).hasSize(2);
        assertThat(graph.edges()).filteredOn(edge -> edge.type() == FactEdgeType.CALLS
                && edge.role().equals("SUPER_CONSTRUCTOR"))
            .singleElement();
        assertThat(graph.nodes()).extracting(FactNode::type)
            .contains(FactNodeType.CONDITION, FactNodeType.THROW);
    }

    @Test void lambdaAndMethodReferenceReachSourceMethods() throws Exception {
        write("demo/controller/ValueController.java", """
            package demo.controller;
            import demo.model.Value;
            import java.util.List;
            public class ValueController {
                public void save(List<String> values) {
                    values.stream().map(value -> Value.create(value)).map(Value::validate).toList();
                }
            }
            """);
        write("demo/model/Value.java", """
            package demo.model;
            public class Value {
                public static Value create(String value) { return new Value(); }
                public String validate() { return "ok"; }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.ValueController", "save"), source(2),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        FactNode lambda = graph.nodes().stream().filter(node -> node.type() == FactNodeType.LAMBDA)
            .findFirst().orElseThrow();
        assertThat(graph.nodes()).extracting(FactNode::type)
            .contains(FactNodeType.LAMBDA, FactNodeType.METHOD_REFERENCE);
        assertThat(graph.edges()).filteredOn(edge -> edge.sourceNodeId().equals(lambda.id())
                && edge.type() == FactEdgeType.CALLS)
            .isNotEmpty();
        assertThat(graph.edges()).filteredOn(edge -> edge.type() == FactEdgeType.REFERENCES
                && edge.role().equals("TARGET"))
            .singleElement();
    }

    @Test void preservesUnaryNestedBooleanAndSwitchCaseStructure() throws Exception {
        write("demo/controller/RuleController.java", """
            package demo.controller;
            public class RuleController {
                enum Type { A, B }
                public boolean validate(Type type, long deposit, long rent) {
                    if (!(deposit > 0 && rent > 0)) throw new IllegalArgumentException();
                    return switch (type) {
                        case A -> deposit <= 0;
                        case B -> rent <= 0 || deposit <= 0;
                    };
                }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.RuleController", "validate"), source(1),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        assertThat(graph.nodes()).withFailMessage("nodes=%s edges=%s", graph.nodes(), graph.edges())
            .filteredOn(node -> node.payload() instanceof FactNodePayload.ConditionPayload)
            .extracting(node -> ((FactNodePayload.ConditionPayload) node.payload()).rootOperator())
            .contains("!", "&&", ">", "==", "<=", "||");
        assertThat(graph.edges()).filteredOn(edge -> edge.role().equals("SWITCH_CASE"))
            .hasSize(2);
        assertThat(graph.edges()).filteredOn(edge -> edge.role().equals("SWITCH_RESULT"))
            .hasSize(2);
    }

    @Test void connectsInvocationArgumentsToTargetParametersByOrdinal() throws Exception {
        write("demo/controller/InputController.java", """
            package demo.controller;
            import demo.service.InputService;
            public class InputController { private InputService service;
                public void save(String requestValue) { service.save(requestValue); }
            }
            """);
        write("demo/service/InputService.java", """
            package demo.service;
            public class InputService {
                public void save(String domainValue) {
                    if (domainValue == null) throw new IllegalArgumentException();
                }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.InputController", "save"), source(2),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        FactNode targetParameter = graph.nodes().stream()
            .filter(node -> node.payload() instanceof FactNodePayload.ParameterPayload parameter
                && parameter.name().equals("domainValue"))
            .findFirst().orElseThrow();
        assertThat(graph.edges()).filteredOn(edge -> edge.sourceNodeId().equals(targetParameter.id())
                && edge.type() == FactEdgeType.ORIGINATES_FROM
                && edge.role().equals("CALL_ARGUMENT"))
            .singleElement();
    }

    @Test void mapsThrownExceptionThroughHandlerToHttpStatus() throws Exception {
        write("demo/controller/GuardController.java", """
            package demo.controller;
            public class GuardController {
                public void validate(String value) {
                    if (value == null) throw new IllegalArgumentException();
                }
            }
            """);
        write("demo/config/GlobalExceptionHandler.java", """
            package demo.config;
            public class GlobalExceptionHandler {
                @ExceptionHandler(IllegalArgumentException.class)
                public Object badRequest(IllegalArgumentException exception) {
                    ErrorCode code = ErrorCode.BAD_REQUEST;
                    return code;
                }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.GuardController", "validate"), source(2),
            FactGraphTraversalBudget.defaults()).graphs().get(0);

        assertThat(graph.nodes()).extracting(FactNode::type)
            .contains(FactNodeType.EXCEPTION, FactNodeType.EXCEPTION_HANDLER, FactNodeType.HTTP_STATUS);
        assertThat(graph.nodes()).filteredOn(node -> node.type() == FactNodeType.HTTP_STATUS)
            .singleElement().satisfies(node -> assertThat(
                ((FactNodePayload.HttpStatusPayload) node.payload()).statusCode()).isEqualTo(400));
        assertThat(graph.edges()).extracting(FactEdge::type)
            .contains(FactEdgeType.THROWS, FactEdgeType.HANDLED_BY, FactEdgeType.MAPS_TO);
    }

    @Test void delegatedGuardKeepsCalledMethodReturnEvidence() throws Exception {
        write("demo/controller/OrderController.java", """
            package demo.controller;
            import demo.service.OrderService;
            public class OrderController { private OrderService service;
                public void ship(long version) { service.ship(version); }
            }
            """);
        write("demo/service/OrderService.java", """
            package demo.service;
            public class OrderService { private Order order;
                public void ship(long version) { if (order.matchVersion(version)) throw new IllegalStateException(); }
            }
            class Order { private long version; boolean matchVersion(long supplied) { return version == supplied; } }
            """);
        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.OrderController", "ship"), source(2), FactGraphTraversalBudget.defaults()).graphs().get(0);
        assertThat(graph.edges()).extracting(FactEdge::type).contains(FactEdgeType.RETURNS);
        var scope = new io.atworks.specscan.analysis.domain.rule.MethodScope(graph.graphId(),
            graph.nodes().stream().filter(node -> node.type() == FactNodeType.API_METHOD
                || node.type() == FactNodeType.METHOD).map(FactNode::id).collect(java.util.stream.Collectors.toSet()));
        var predicates = new DefaultValidationCandidateDetector(List.of()).detect(graph, scope);
        assertThat(predicates).singleElement();
        assertThat(new DelegatedGuardRule().match(graph, predicates.get(0)))
            .withFailMessage("nodes=%s edges=%s", graph.nodes(), graph.edges()).isNotEmpty();
        GraphRuleEngineResult result = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()),
            InitialRulePacks.all()).evaluate(graph, scope);
        assertThat(result.candidates().businessRules()).extracting("category")
            .contains(io.atworks.specscan.analysis.domain.candidate.BusinessRuleCategory.VERSION_CONSISTENCY);
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

    @Test void recordsBuilderAndConstructorValueFlowsWithoutInventingMissingFields() throws Exception {
        write("demo/controller/PropertyController.java", """
            package demo.controller;
            import demo.model.Property;
            import demo.dto.PropertyResponse;
            public class PropertyController {
                public PropertyResponse get(Property property) { return PropertyResponse.to(property); }
            }
            """);
        write("demo/model/Property.java", """
            package demo.model;
            public class Property { public Long getId() { return 1L; } }
            """);
        write("demo/dto/PropertyResponse.java", """
            package demo.dto;
            import demo.model.Property;
            public class PropertyResponse {
                public static Builder builder() { return new Builder(); }
                public static PropertyResponse to(Property property) {
                    return builder().propertyId(property.getId()).build();
                }
                public static class Builder {
                    public Builder propertyId(Long value) { return this; }
                    public PropertyResponse build() { return new PropertyResponse(); }
                }
            }
            """);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(
            scan("demo.controller.PropertyController", "get"), source(3),
            FactGraphTraversalBudget.defaults()).graphs().get(0);
        FactNode sourceValue = graph.nodes().stream().filter(node -> node.type() == FactNodeType.METHOD_CALL
            && node.snippet().equals("property.getId()" )).findFirst().orElseThrow();
        FactNode responseField = graph.nodes().stream().filter(node -> node.type() == FactNodeType.VALUE_FIELD
            && node.snippet().equals("propertyId")).findFirst().orElseThrow();

        assertThat(graph.edges()).anySatisfy(edge -> {
            assertThat(edge.sourceNodeId()).isEqualTo(sourceValue.id());
            assertThat(edge.targetNodeId()).isEqualTo(responseField.id());
            assertThat(edge.type()).isEqualTo(FactEdgeType.VALUE_FLOWS_TO);
        });
        assertThat(graph.nodes()).filteredOn(node -> node.type() == FactNodeType.VALUE_FIELD)
            .extracting(FactNode::snippet).doesNotContain("missingField");
    }

    private void write(String relative, String content) throws Exception { Path file = workspace.resolve("src/main/java").resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, content); }
    private StaticScanResult scan(String controller, String method) { SourceTrace trace = new SourceTrace("src/main/java/" + controller.replace('.', '/') + ".java", 1, 1); ApiEndpoint endpoint = new ApiEndpoint("POST", "/login", controller, method, List.of(), new ResponseBinding("void", trace), trace); return new StaticScanResult(List.of(endpoint), 2, List.of(), null); }
    private RepositorySource source(int files) { return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"), new WorkspaceContext("exec", workspace.toString(), Instant.now(), "cache", false), List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, files, files, 1, "DETECTED")), "Gradle", new JavaInventorySummary(files, 1, 1, true, 0), List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"), new IngestionMetadata(Instant.now(), Instant.now(), files, "LOCAL", "main", 0)); }
}
