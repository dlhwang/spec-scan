package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.ConditionLocation;
import io.atworks.specscan.analysis.domain.GraphEdge;
import io.atworks.specscan.analysis.domain.GraphEdgeType;
import io.atworks.specscan.analysis.domain.GraphNode;
import io.atworks.specscan.analysis.domain.GraphNodeType;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.ResponseBinding;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedStaticAnalysisBaselineTest {

    private final NormalizationService normalizationService = new NormalizationService();

    @Test
    void baselineManifestSeparatesSyntheticBoundaryAndHoldoutInputs() throws IOException {
        String manifest = resource("rule-based-static-analysis/baseline-expectations.json");

        assertThat(manifest)
            .contains("synthetic/false-positive")
            .contains("synthetic/type-resolution-failure")
            .contains("\"dataset\": \"holdout\"")
            .contains("\"disposition\": \"PRESERVE\"")
            .contains("\"disposition\": \"REPLACE\"")
            .contains("\"disposition\": \"UNSUPPORTED\"");

        assertThat(resource("rule-based-static-analysis/synthetic/false-positive/UnrelatedMatchesService.java"))
            .contains("reference.matches")
            .doesNotContain("PasswordEncoder");
        assertThat(resource("rule-based-static-analysis/synthetic/type-resolution-failure/UnresolvedReceiverService.java"))
            .contains("MissingCredentialVerifier");
        assertThat(resource("rule-based-static-analysis/holdout/RenamedDomainGuard.java"))
            .doesNotContain("PAYMENT_WAITING", "PREPARING", "currentUser", "permission", "version");
    }

    @Test
    void replacementBaselineCapturesCurrentPermissionHardcoding() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/example/AccessService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "permission-baseline",
            "SERVICE_HINT",
            "accessCheck",
            "if (!accessPolicy.hasPermission(resource, actor)) { throw new AccessDeniedException(); }",
            0.5,
            trace
        );
        ApiEndpoint endpoint = endpoint("GET", "/resources/{id}", "example.AccessController", trace);
        ValidationEvidenceGraph graph = graphFor(
            endpoint,
            trace,
            "PERMISSION_CHECK",
            "!accessPolicy.hasPermission(resource, actor)",
            candidate.evidenceSnippet()
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).singleElement().satisfies(condition -> {
            assertThat(condition.targetLocation()).isEqualTo(ConditionLocation.AUTH);
            assertThat(condition.targetPath()).isEqualTo("$.currentUser");
            assertThat(condition.operator()).isEqualTo("HAS_CANCELLATION_PERMISSION");
        });
    }

    @Test
    void unsupportedBaselineCapturesReachableGenericRuleRejection() throws IngestionException {
        SourceTrace trace = new SourceTrace("src/main/java/example/ThresholdService.java", 20, 24);
        ValidationCandidate candidate = new ValidationCandidate(
            "generic-baseline",
            "SERVICE_HINT",
            "amount",
            "if (request.amount() < account.minimum()) { throw new IllegalArgumentException(); }",
            0.5,
            trace
        );
        ApiEndpoint endpoint = endpoint("POST", "/threshold", "example.ThresholdController", trace);
        ValidationEvidenceGraph graph = graphFor(
            endpoint,
            trace,
            "GENERIC",
            "request.amount() < account.minimum()",
            candidate.evidenceSnippet()
        );

        NormalizedResult result = normalizationService.normalize(List.of(candidate), List.of(endpoint), graph);

        assertThat(result.conditions()).isEmpty();
        assertThat(result.rejected()).containsExactly(candidate);
        assertThat(result.warnings())
            .extracting(warning -> warning.warningCode() + ":" + warning.details().get("reasonCategory"))
            .containsExactly("SERVICE_HINT_REJECTED:NO_QUALIFYING_RULE");
    }

    private ApiEndpoint endpoint(String method, String path, String controllerClass, SourceTrace trace) {
        return new ApiEndpoint(
            method,
            path,
            controllerClass,
            "handle",
            List.of(new RequestBinding("id", BindingLocation.PATH, "String", true, null, null, null, List.of(), trace)),
            new ResponseBinding("void", trace),
            trace
        );
    }

    private ValidationEvidenceGraph graphFor(
        ApiEndpoint endpoint,
        SourceTrace trace,
        String ruleClass,
        String condition,
        String snippet
    ) {
        String endpointId = "ENDPOINT:" + endpoint.httpMethod() + ":" + endpoint.path();
        String serviceId = "SERVICE_METHOD:example.Service.handle";
        String ruleId = "BUSINESS_RULE:example.Service.handle:" + ruleClass + ":1";
        return new ValidationEvidenceGraph(
            List.of(
                new GraphNode(endpointId, GraphNodeType.ENDPOINT, endpoint.httpMethod() + " " + endpoint.path(),
                    "Controller.java", 10, "Controller.handle"),
                new GraphNode(serviceId, GraphNodeType.SERVICE_METHOD, "Service.handle",
                    "Service.java", 18, "void handle()"),
                new GraphNode(ruleId, GraphNodeType.BUSINESS_RULE, ruleClass + " | " + condition,
                    trace.fileRelativePath(), 22, snippet)
            ),
            List.of(
                new GraphEdge(endpointId, serviceId, GraphEdgeType.CALLS, "service.handle(...)"),
                new GraphEdge(serviceId, ruleId, GraphEdgeType.EVALUATES, ruleClass + " | if statement")
            )
        );
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
