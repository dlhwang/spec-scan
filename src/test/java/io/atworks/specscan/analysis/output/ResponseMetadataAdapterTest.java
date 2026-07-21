package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.output.ResponseMetadataAdapter;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseMetadataAdapterTest {
    private final ResponseMetadataAdapter adapter = new ResponseMetadataAdapter();

    @Test void createsStatusAssertionOnlyForExplicitMetadata() {
        SourceTrace trace = new SourceTrace("Controller.java", 10, 10);
        ApiEndpoint endpoint = endpoint(new ResponseBinding("Property", trace, 201, "@ResponseStatus"), trace);
        EndpointRuleOutput result = adapter.augment(endpoint, EndpointRuleOutput.empty(endpoint.path()));
        assertThat(result.responseAssertions()).singleElement().satisfies(assertion -> {
            assertThat(assertion.targetLocation()).isEqualTo("STATUS");
            assertThat(assertion.expectedValues()).containsExactly("201");
        });
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test void preservesUnresolvedDiagnosticWithoutInventing200() {
        SourceTrace trace = new SourceTrace("Controller.java", 10, 10);
        EndpointRuleOutput result = adapter.augment(endpoint(new ResponseBinding("Property", trace), trace),
            EndpointRuleOutput.empty("/properties"));
        assertThat(result.responseAssertions()).isEmpty();
        assertThat(result.diagnostics()).extracting("code").containsExactly("RESPONSE_METADATA_UNRESOLVED");
    }

    @Test void createsFrameworkDefault200OnlyForSpringMappedHandler() {
        SourceTrace trace = new SourceTrace("Controller.java", 10, 10);
        ApiEndpoint endpoint = endpoint(new ResponseBinding("Property", trace), trace);
        SourceRange range = new SourceRange("Controller.java", 10, 1, 10, 40);
        FactNode api = new FactNode("api", FactNodeType.API_METHOD, range, "create()",
            TypeResolution.notApplicable(), new FactNodePayload.MethodPayload("Controller", "create()", true));
        FactNode annotation = new FactNode("mapping", FactNodeType.ANNOTATION, range, "@PostMapping",
            TypeResolution.notApplicable(), new FactNodePayload.AnnotationPayload("PostMapping", java.util.Map.of()));
        FactEdge edge = new FactEdge("edge", "api", "mapping", FactEdgeType.HAS_ANNOTATION, 0,
            "METHOD_ANNOTATION");
        FactCodeGraph graph = new FactCodeGraph("graph", "api", List.of(api, annotation), List.of(edge));

        EndpointRuleOutput result = adapter.augment(endpoint, EndpointRuleOutput.empty(endpoint.path()), graph);

        assertThat(result.responseAssertions()).singleElement().satisfies(assertion -> {
            assertThat(assertion.expectedValues()).containsExactly("200");
            assertThat(assertion.ruleId()).isEqualTo("SPRING_MVC_DEFAULT_RESPONSE_STATUS");
            assertThat(assertion.evidence()).extracting("nodeId").containsExactly("mapping");
        });
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test void createsLiteralResponseHeaderAssertion() {
        SourceTrace trace = new SourceTrace("Controller.java", 10, 10);
        ResponseBinding binding = new ResponseBinding("Property", trace, 201, "ResponseEntity.status",
            java.util.Map.of("Location", "/properties/1"));
        EndpointRuleOutput result = adapter.augment(endpoint(binding, trace), EndpointRuleOutput.empty("/properties"));
        assertThat(result.responseAssertions()).extracting("targetLocation")
            .containsExactly("STATUS", "RESPONSE_HEADER");
        assertThat(result.responseAssertions().get(1).targetPath()).isEqualTo("$.Location");
    }

    @Test void reportsConflictingStatusesWithoutChoosingOne() {
        SourceTrace trace = new SourceTrace("Controller.java", 10, 10);
        ResponseBinding binding = new ResponseBinding("Property", trace, null, "CONFLICT:[200, 204]",
            java.util.Map.of());
        EndpointRuleOutput result = adapter.augment(endpoint(binding, trace), EndpointRuleOutput.empty("/properties"));
        assertThat(result.responseAssertions()).isEmpty();
        assertThat(result.diagnostics()).extracting("code").containsExactly("RESPONSE_METADATA_CONFLICT");
    }

    @Test void createsNoContentStatusAndEmptyBodyFromGraphEvidence() {
        SourceTrace trace = new SourceTrace("Controller.java", 10, 10);
        ApiEndpoint endpoint = endpoint(new ResponseBinding("Void", trace, 204,
            "ResponseEntity.noContent"), trace);
        SourceRange range = new SourceRange("Controller.java", 10, 1, 10, 40);
        FactNode api = new FactNode("api", FactNodeType.API_METHOD, range, "delete()",
            TypeResolution.notApplicable(), new FactNodePayload.MethodPayload("Controller", "delete()", true));
        FactNode call = new FactNode("no-content", FactNodeType.METHOD_CALL, range,
            "ResponseEntity.noContent()", TypeResolution.notApplicable(),
            new FactNodePayload.MethodCallPayload("noContent", 0, false));
        FactEdge edge = new FactEdge("edge", "api", "no-content", FactEdgeType.CALLS, -1, "CALL");
        FactCodeGraph graph = new FactCodeGraph("graph", "api", List.of(api, call), List.of(edge));

        EndpointRuleOutput result = adapter.augment(endpoint, EndpointRuleOutput.empty(endpoint.path()), graph);

        assertThat(result.responseAssertions()).extracting("targetPath", "operator", "expectedValues")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("$status", "EQ", List.of("204")),
                org.assertj.core.groups.Tuple.tuple("$body", "EMPTY", List.of()));
        assertThat(result.responseAssertions()).allSatisfy(assertion ->
            assertThat(assertion.evidence()).extracting("nodeId").contains("no-content"));
    }

    private ApiEndpoint endpoint(ResponseBinding response, SourceTrace trace) {
        return new ApiEndpoint("POST", "/properties", "Controller", "create", List.of(), response, trace);
    }
}
