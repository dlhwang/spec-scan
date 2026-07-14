package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
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

    private ApiEndpoint endpoint(ResponseBinding response, SourceTrace trace) {
        return new ApiEndpoint("POST", "/properties", "Controller", "create", List.of(), response, trace);
    }
}
