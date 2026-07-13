package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationServiceTest {

    private NormalizationService normalizationService;

    @BeforeEach
    void setUp() {
        normalizationService = new NormalizationService();
    }

    @Test
    void testCandidateChunkingAndNormalization() throws IngestionException {
        // Given
        SourceTrace trace1 = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 12);
        SourceTrace trace2 = new SourceTrace("src/main/java/io/atworks/service/UserService.java", 20, 25);
        SourceTrace invalidTrace = new SourceTrace(null, 0, 0); // null path to trigger invalid chunk

        ValidationCandidate cand1 = new ValidationCandidate("cand-1", "CUSTOM_ANNOTATION", "email", "@ValidEmail", 1.0, trace1);
        ValidationCandidate cand2 = new ValidationCandidate("cand-2", "SERVICE_HINT", "age", "user.getAge() < 19", 0.5, trace2);
        
        // candidate with missing trace to trigger validation failure
        ValidationCandidate candInvalid = new ValidationCandidate("cand-3", "VALIDATOR", "name", "name error", 1.0, invalidTrace);

        List<ValidationCandidate> candidates = List.of(cand1, cand2, candInvalid);

        // Mock Endpoint
        RequestBinding bodyBinding = new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), trace1);
        ResponseBinding responseBinding = new ResponseBinding("void", trace1);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(bodyBinding),
            responseBinding,
            trace1
        );
        List<ApiEndpoint> endpoints = List.of(endpoint);

        // When
        NormalizedResult result = normalizationService.normalize(candidates, endpoints);

        // Then
        // 1. Only graph-independent candidates normalize without graph-backed service evidence.
        assertThat(result.conditions()).hasSize(1);

        ApiCondition emailCond = result.conditions().stream()
                .filter(c -> c.targetPath().equals("$.email")).findFirst().orElseThrow();
        assertThat(emailCond.targetLocation()).isEqualTo(ConditionLocation.BODY);
        assertThat(emailCond.operator()).isEqualTo("EMAIL");
        assertThat(emailCond.expected()).isEqualTo("email pattern");

        // 2. Invalid chunk detection (cand-3 is grouped to global because of null trace, which triggers invalid validation)
        assertThat(result.invalidChunks()).hasSize(1);
        CandidateChunk invalidChunk = result.invalidChunks().get(0);
        assertThat(invalidChunk.sourceType()).isEqualTo("VALIDATOR");

        // 3. Rejected verification
        assertThat(result.rejected()).extracting(ValidationCandidate::candidateId)
            .containsExactlyInAnyOrder("cand-2", "cand-3");

    }

    @Test
    void graphAwareNormalizationRejectsUnsupportedGenericServiceHintRules() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 12);
        ValidationCandidate candidate = new ValidationCandidate("cand-1", "SERVICE_HINT", "age", "user.getAge() < 19", 0.5, trace);

        RequestBinding bodyBinding = new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), trace);
        ResponseBinding responseBinding = new ResponseBinding("void", trace);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(bodyBinding),
            responseBinding,
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/users", GraphNodeType.ENDPOINT, "POST /users", "UserController.java", 10,
                    "UserController.createUser"),
                new GraphNode("SERVICE_METHOD:UserService.register", GraphNodeType.SERVICE_METHOD, "UserService.register",
                    "UserService.java", 20, "public void register(UserDto user)"),
                new GraphNode("BUSINESS_RULE:UserService.register:GENERIC:1", GraphNodeType.BUSINESS_RULE,
                    "GENERIC | user.getAge() < 19", "UserService.java", 22, "if (user.getAge() < 19) throw new IllegalArgumentException();")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/users", "SERVICE_METHOD:UserService.register", GraphEdgeType.CALLS,
                    "userService.register(user)"),
                new GraphEdge("SERVICE_METHOD:UserService.register", "BUSINESS_RULE:UserService.register:GENERIC:1",
                    GraphEdgeType.EVALUATES, "if statement")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).isEmpty();
        assertThat(result.rejected()).containsExactly(candidate);
        assertThat(result.warnings()).extracting(warning -> warning.warningCode() + ":" + warning.details().get("reasonCategory"))
            .containsExactly("SERVICE_HINT_REJECTED:NO_QUALIFYING_RULE");
    }

    @Test
    void serviceHintMapsVersionConflictToQueryCondition() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/ShippingService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "version",
            "if (order.matchVersion(req.getVersion())) { throw new VersionConflictException(); }",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/admin/orders/{orderNo}/shipping",
            "io.atworks.controller.AdminOrderController",
            "startShippingOrder",
            List.of(
                new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace),
                new RequestBinding("version", BindingLocation.QUERY, "long", true, null, null, null, List.of(), trace)
            ),
            new ResponseBinding("String", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/admin/orders/{orderNo}/shipping", GraphNodeType.ENDPOINT,
                    "POST /admin/orders/{orderNo}/shipping", "AdminOrderController.java", 10, "AdminOrderController.startShippingOrder"),
                new GraphNode("SERVICE_METHOD:StartShippingService.startShipping", GraphNodeType.SERVICE_METHOD,
                    "StartShippingService.startShipping", "ShippingService.java", 18, "void startShipping()"),
                new GraphNode("BUSINESS_RULE:StartShippingService.startShipping:VERSION_CHECK:1", GraphNodeType.BUSINESS_RULE,
                    "VERSION_CHECK | order.matchVersion(req.getVersion())", "src/main/java/io/atworks/service/ShippingService.java", 22,
                    "if (order.matchVersion(req.getVersion())) { throw new VersionConflictException(); }")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/admin/orders/{orderNo}/shipping", "SERVICE_METHOD:StartShippingService.startShipping",
                    GraphEdgeType.CALLS, "startShippingService.startShipping(...)"),
                new GraphEdge("SERVICE_METHOD:StartShippingService.startShipping", "BUSINESS_RULE:StartShippingService.startShipping:VERSION_CHECK:1",
                    GraphEdgeType.EVALUATES, "VERSION_CHECK | if statement")
            )
        );


        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).hasSize(1);
        ApiCondition condition = result.conditions().get(0);
        assertThat(condition.targetLocation()).isEqualTo(ConditionLocation.QUERY);
        assertThat(condition.targetPath()).isEqualTo("$.version");
        assertThat(condition.operator()).isEqualTo("OPTIMISTIC_LOCK_MATCH");
    }

    @Test
    void serviceHintMapsPermissionRuleToAuthCondition() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/CancelOrderService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "hasCancellationPermission",
            "if (!cancelPolicy.hasCancellationPermission(order, canceller)) { throw new NoCancellablePermission(); }",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "GET",
            "/my/orders/{orderNo}/cancel",
            "io.atworks.controller.CancelOrderController",
            "cancelOrder",
            List.of(new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("String", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:GET:/my/orders/{orderNo}/cancel", GraphNodeType.ENDPOINT,
                    "GET /my/orders/{orderNo}/cancel", "CancelOrderController.java", 10, "CancelOrderController.cancelOrder"),
                new GraphNode("SERVICE_METHOD:CancelOrderService.cancel", GraphNodeType.SERVICE_METHOD,
                    "CancelOrderService.cancel", "CancelOrderService.java", 18, "void cancel()"),
                new GraphNode("BUSINESS_RULE:CancelOrderService.cancel:PERMISSION_CHECK:1", GraphNodeType.BUSINESS_RULE,
                    "PERMISSION_CHECK | !cancelPolicy.hasCancellationPermission(order, canceller)", "src/main/java/io/atworks/service/CancelOrderService.java", 22,
                    "if (!cancelPolicy.hasCancellationPermission(order, canceller)) { throw new NoCancellablePermission(); }")
            ),
            List.of(
                new GraphEdge("ENDPOINT:GET:/my/orders/{orderNo}/cancel", "SERVICE_METHOD:CancelOrderService.cancel",
                    GraphEdgeType.CALLS, "cancelOrderService.cancel(...)"),
                new GraphEdge("SERVICE_METHOD:CancelOrderService.cancel", "BUSINESS_RULE:CancelOrderService.cancel:PERMISSION_CHECK:1",
                    GraphEdgeType.EVALUATES, "PERMISSION_CHECK | if statement")
            )
        );


        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).hasSize(1);
        ApiCondition condition = result.conditions().get(0);
        assertThat(condition.targetLocation()).isEqualTo(ConditionLocation.AUTH);
        assertThat(condition.targetPath()).isEqualTo("$.currentUser");
        assertThat(condition.operator()).isEqualTo("HAS_CANCELLATION_PERMISSION");
    }

    @Test
    void serviceHintMapsGraphBackedOptionalLookupToExistenceCondition() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/ShippingService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "orderNo",
            "orderRepository.findById(new OrderNo(req.getOrderNumber())) -> orderOpt.orElseThrow(() -> new NoOrderException())",
            0.6,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/admin/orders/{orderNo}/shipping",
            "io.atworks.controller.AdminOrderController",
            "startShippingOrder",
            List.of(new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("String", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/admin/orders/{orderNo}/shipping", GraphNodeType.ENDPOINT,
                    "POST /admin/orders/{orderNo}/shipping", "AdminOrderController.java", 10, "AdminOrderController.startShippingOrder"),
                new GraphNode("SERVICE_METHOD:StartShippingService.startShipping", GraphNodeType.SERVICE_METHOD,
                    "StartShippingService.startShipping", "ShippingService.java", 18, "void startShipping()"),
                new GraphNode("BUSINESS_RULE:StartShippingService.startShipping:EXISTENCE_CHECK:1", GraphNodeType.BUSINESS_RULE,
                    "EXISTENCE_CHECK | orderRepository.findById(new OrderNo(req.getOrderNumber()))", "src/main/java/io/atworks/service/ShippingService.java", 22,
                    "orderRepository.findById(new OrderNo(req.getOrderNumber())).orElseThrow(() -> new NoOrderException())")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/admin/orders/{orderNo}/shipping", "SERVICE_METHOD:StartShippingService.startShipping",
                    GraphEdgeType.CALLS, "startShippingService.startShipping(...)"),
                new GraphEdge("SERVICE_METHOD:StartShippingService.startShipping", "BUSINESS_RULE:StartShippingService.startShipping:EXISTENCE_CHECK:1",
                    GraphEdgeType.EVALUATES, "EXISTENCE_CHECK | optional lookup")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).hasSize(1);
        ApiCondition condition = result.conditions().get(0);
        assertThat(condition.targetLocation()).isEqualTo(ConditionLocation.PATH);
        assertThat(condition.targetPath()).isEqualTo("$.orderNo");
        assertThat(condition.operator()).isEqualTo("EXISTS_IN_REPOSITORY");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void serviceHintMapsStateGuardToResourceCondition() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/Order.java", 120, 128);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "order.state",
            "if (!isNotYetShipped()) throw new AlreadyShippedException();",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "GET",
            "/my/orders/{orderNo}/cancel",
            "io.atworks.controller.CancelOrderController",
            "cancelOrder",
            List.of(new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("String", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:GET:/my/orders/{orderNo}/cancel", GraphNodeType.ENDPOINT,
                    "GET /my/orders/{orderNo}/cancel", "CancelOrderController.java", 10, "CancelOrderController.cancelOrder"),
                new GraphNode("SERVICE_METHOD:Order.cancel", GraphNodeType.SERVICE_METHOD,
                    "Order.cancel", "Order.java", 110, "void cancel()"),
                new GraphNode("BUSINESS_RULE:Order.verifyNotYetShipped:GENERIC:1", GraphNodeType.BUSINESS_RULE,
                    "GENERIC | !isNotYetShipped()", "src/main/java/io/atworks/service/Order.java", 126,
                    "if (!isNotYetShipped()) throw new AlreadyShippedException();")
            ),
            List.of(
                new GraphEdge("ENDPOINT:GET:/my/orders/{orderNo}/cancel", "SERVICE_METHOD:Order.cancel", GraphEdgeType.CALLS,
                    "cancelOrderService.cancel(...)"),
                new GraphEdge("SERVICE_METHOD:Order.cancel", "BUSINESS_RULE:Order.verifyNotYetShipped:GENERIC:1",
                    GraphEdgeType.EVALUATES, "GENERIC | if statement")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).hasSize(1);
        ApiCondition condition = result.conditions().get(0);
        assertThat(condition.targetLocation()).isEqualTo(ConditionLocation.RESOURCE);
        assertThat(condition.targetPath()).isEqualTo("$.order.state");
        assertThat(condition.operator()).isEqualTo("STATE_IN");
        assertThat(condition.expected()).isEqualTo("PAYMENT_WAITING,PREPARING");
    }

    @Test
    void graphDerivedStateGuardIsAddedForServiceHintChunk() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/ShippingService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "version",
            "if (order.matchVersion(req.getVersion())) { throw new VersionConflictException(); }",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/admin/orders/{orderNo}/shipping",
            "io.atworks.controller.AdminOrderController",
            "startShippingOrder",
            List.of(
                new RequestBinding("orderNo", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace),
                new RequestBinding("version", BindingLocation.QUERY, "long", true, null, null, null, List.of(), trace)
            ),
            new ResponseBinding("String", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/admin/orders/{orderNo}/shipping", GraphNodeType.ENDPOINT,
                    "POST /admin/orders/{orderNo}/shipping", "AdminOrderController.java", 10, "AdminOrderController.startShippingOrder"),
                new GraphNode("SERVICE_METHOD:StartShippingService.startShipping", GraphNodeType.SERVICE_METHOD,
                    "StartShippingService.startShipping", "ShippingService.java", 18, "void startShipping()"),
                new GraphNode("BUSINESS_RULE:StartShippingService.startShipping:VERSION_CHECK:1", GraphNodeType.BUSINESS_RULE,
                    "VERSION_CHECK | order.matchVersion(req.getVersion())", "src/main/java/io/atworks/service/ShippingService.java", 22,
                    "if (order.matchVersion(req.getVersion())) { throw new VersionConflictException(); }"),
                new GraphNode("SERVICE_METHOD:Order.isNotYetShipped", GraphNodeType.SERVICE_METHOD,
                    "Order.isNotYetShipped", "Order.java", 130,
                    "public boolean isNotYetShipped() { return state == OrderState.PAYMENT_WAITING || state == OrderState.PREPARING; }")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/admin/orders/{orderNo}/shipping", "SERVICE_METHOD:StartShippingService.startShipping",
                    GraphEdgeType.CALLS, "startShippingService.startShipping(...)"),
                new GraphEdge("SERVICE_METHOD:StartShippingService.startShipping", "BUSINESS_RULE:StartShippingService.startShipping:VERSION_CHECK:1",
                    GraphEdgeType.EVALUATES, "VERSION_CHECK | if statement"),
                new GraphEdge("SERVICE_METHOD:StartShippingService.startShipping", "SERVICE_METHOD:Order.isNotYetShipped",
                    GraphEdgeType.CALLS, "order.startShipping() -> verifyNotYetShipped()")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).extracting(ApiCondition::operator)
            .contains("OPTIMISTIC_LOCK_MATCH", "STATE_IN");
    }

    @Test
    void serviceHintWithoutGraphEvidenceIsRejected() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/VisitService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "petId",
            "if (visit.getPetId() <= 0) { throw new IllegalArgumentException(\"invalid\"); }",
            0.5,
            trace
        );

        RequestBinding bodyBinding = new RequestBinding("visit", BindingLocation.BODY, "Visit", true, null, null, null, List.of(), trace);
        ResponseBinding responseBinding = new ResponseBinding("void", trace);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/owners/*/pets/{petId}/visits",
            "io.atworks.controller.VisitController",
            "create",
            List.of(bodyBinding, new RequestBinding("petId", BindingLocation.PATH, "Integer", true, null, null, null, List.of(), trace)),
            responseBinding,
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:GET:/other", GraphNodeType.ENDPOINT, "GET /other", "OtherController.java", 5,
                    "OtherController.read")
            ),
            List.of()
        );
        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).isEmpty();
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.warnings()).extracting(warning -> warning.warningCode() + ":" + warning.details().get("reasonCategory"))
            .containsExactly("SERVICE_HINT_REJECTED:UNREACHABLE_GRAPH_EVIDENCE");
    }

    @Test
    void serviceHintWithoutReachableRuleNodeIsRejected() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/VisitService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "petId",
            "if (visit.getPetId() <= 0) { throw new IllegalArgumentException(\"invalid\"); }",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/owners/*/pets/{petId}/visits",
            "io.atworks.controller.VisitController",
            "create",
            List.of(
                new RequestBinding("visit", BindingLocation.BODY, "Visit", true, null, null, null, List.of(), trace),
                new RequestBinding("petId", BindingLocation.PATH, "Integer", true, null, null, null, List.of(), trace)
            ),
            new ResponseBinding("void", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/owners/*/pets/{petId}/visits", GraphNodeType.ENDPOINT,
                    "POST /owners/*/pets/{petId}/visits", "VisitController.java", 10, "VisitController.create"),
                new GraphNode("SERVICE_METHOD:VisitService.create", GraphNodeType.SERVICE_METHOD,
                    "VisitService.create", "VisitService.java", 18, "void create()")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/owners/*/pets/{petId}/visits", "SERVICE_METHOD:VisitService.create",
                    GraphEdgeType.CALLS, "visitService.create(visit)")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).isEmpty();
        assertThat(result.rejected()).containsExactly(candidate);
        assertThat(result.warnings()).extracting(warning -> warning.warningCode() + ":" + warning.details().get("reasonCategory"))
            .containsExactly("SERVICE_HINT_REJECTED:UNREACHABLE_GRAPH_EVIDENCE");

    }

    @Test
    void serviceHintWithAmbiguousReachableRulesIsRejected() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/UserService.java", 20, 40);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "age",
            "if (user.getAge() < 19) { throw new IllegalArgumentException(); }",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), trace)),
            new ResponseBinding("void", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/users", GraphNodeType.ENDPOINT, "POST /users", "UserController.java", 10, "UserController.createUser"),
                new GraphNode("SERVICE_METHOD:UserService.register", GraphNodeType.SERVICE_METHOD, "UserService.register", "UserService.java", 18, "void register()"),
                new GraphNode("BUSINESS_RULE:UserService.register:GENERIC:1", GraphNodeType.BUSINESS_RULE,
                    "GENERIC | user.getAge() < 19", "src/main/java/io/atworks/service/UserService.java", 22,
                    "if (user.getAge() < 19) { throw new IllegalArgumentException(); }"),
                new GraphNode("BUSINESS_RULE:UserService.register:GENERIC:2", GraphNodeType.BUSINESS_RULE,
                    "GENERIC | user.getAge() < 21", "src/main/java/io/atworks/service/UserService.java", 30,
                    "if (user.getAge() < 21) { throw new IllegalArgumentException(); }")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/users", "SERVICE_METHOD:UserService.register", GraphEdgeType.CALLS, "userService.register(user)"),
                new GraphEdge("SERVICE_METHOD:UserService.register", "BUSINESS_RULE:UserService.register:GENERIC:1", GraphEdgeType.EVALUATES, "GENERIC | if statement"),
                new GraphEdge("SERVICE_METHOD:UserService.register", "BUSINESS_RULE:UserService.register:GENERIC:2", GraphEdgeType.EVALUATES, "GENERIC | if statement")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).isEmpty();
        assertThat(result.rejected()).containsExactly(candidate);
        assertThat(result.warnings()).extracting(warning -> warning.warningCode() + ":" + warning.details().get("reasonCategory"))
            .containsExactly("SERVICE_HINT_AMBIGUOUS:AMBIGUOUS_GRAPH_EVIDENCE");

    }

    @Test
    void serviceHintUsesOnlyEndpointReachableRules() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/service/UserService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "cand-1",
            "SERVICE_HINT",
            "age",
            "if (user.getAge() < 19) { throw new IllegalArgumentException(); }",
            0.5,
            trace
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), trace)),
            new ResponseBinding("void", trace),
            trace
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/users", GraphNodeType.ENDPOINT, "POST /users", "UserController.java", 10, "UserController.createUser"),
                new GraphNode("ENDPOINT:POST:/admins", GraphNodeType.ENDPOINT, "POST /admins", "AdminController.java", 10, "AdminController.createAdmin"),
                new GraphNode("SERVICE_METHOD:AdminService.register", GraphNodeType.SERVICE_METHOD, "AdminService.register", "AdminService.java", 18, "void register()"),
                new GraphNode("BUSINESS_RULE:AdminService.register:GENERIC:1", GraphNodeType.BUSINESS_RULE,
                    "GENERIC | user.getAge() < 19", "src/main/java/io/atworks/service/UserService.java", 22,
                    "if (user.getAge() < 19) { throw new IllegalArgumentException(); }")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/admins", "SERVICE_METHOD:AdminService.register", GraphEdgeType.CALLS, "adminService.register(user)"),
                new GraphEdge("SERVICE_METHOD:AdminService.register", "BUSINESS_RULE:AdminService.register:GENERIC:1", GraphEdgeType.EVALUATES, "GENERIC | if statement")
            )
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).isEmpty();
        assertThat(result.rejected()).containsExactly(candidate);
        assertThat(result.warnings()).extracting(warning -> warning.warningCode() + ":" + warning.details().get("reasonCategory"))
            .containsExactly("SERVICE_HINT_REJECTED:CROSS_ENDPOINT_CONTAMINATION_BLOCKED");
    }
}
