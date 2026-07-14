package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.output.RequestBindingConditionAdapter;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RequestBindingConditionAdapterTest {
    @Test void promotesEvidenceBackedRequiredAndEnumBindings() {
        SourceTrace trace = new SourceTrace("src/PropertyController.java", 12, 12);
        ApiEndpoint endpoint = new ApiEndpoint("GET", "/properties", "PropertyController", "get", List.of(
            new RequestBinding("type", BindingLocation.QUERY, "String", true, null, null, null,
                List.of("HOUSE", "ROOM"), trace)), new ResponseBinding("void", trace), trace);

        EndpointRuleOutput result = new RequestBindingConditionAdapter().augment(endpoint,
            EndpointRuleOutput.empty(endpoint.path()));

        assertThat(result.requestPreconditions()).extracting("operator").containsExactly("REQUIRED", "IN");
        assertThat(result.requestPreconditions()).allSatisfy(condition -> assertThat(condition.evidence()).isNotEmpty());
    }
}
