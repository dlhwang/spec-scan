package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.support.output.*;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StructuralConditionAdapterTest {
    @Test void promotesNestedDtoRequiredConditionsWithEvidence() {
        SourceTrace controller = new SourceTrace("src/OrderController.java", 1, 20);
        SourceTrace dto = new SourceTrace("src/OrderRequest.java", 5, 5);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/orders", "OrderController", "order",
            List.of(new RequestBinding("request", BindingLocation.BODY, "OrderRequest", true,
                null, null, null, List.of(), dto)), new ResponseBinding("void", controller), controller);
        ApiCondition nested = new ApiCondition(ConditionLocation.BODY, "$.shippingInfo.address.zipCode",
            "NOT_NULL", null, "@NotNull", 1.0, null, dto, null);

        EndpointRuleOutput result = new StructuralConditionAdapter().augment(endpoint,
            EndpointRuleOutput.empty(endpoint.path()), List.of(nested));

        assertThat(result.requestPreconditions()).singleElement().satisfies(condition -> {
            assertThat(condition.targetPath()).isEqualTo("$.shippingInfo.address.zipCode");
            assertThat(condition.operator()).isEqualTo("NOT_NULL");
            assertThat(condition.evidence()).isNotEmpty();
        });
    }

    @Test void doesNotPromoteRuntimeBusinessOperators() {
        SourceTrace trace = new SourceTrace("src/Order.java", 5, 5);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/orders", "OrderController", "order", List.of(),
            new ResponseBinding("void", trace), trace);
        ApiCondition state = new ApiCondition(ConditionLocation.RESOURCE, "$.order.state", "STATE_IN",
            "PAYMENT_WAITING", "guard", 1.0, null, trace, endpoint.path());
        EndpointRuleOutput result = new StructuralConditionAdapter().augment(endpoint,
            EndpointRuleOutput.empty(endpoint.path()), List.of(state));
        assertThat(result.requestPreconditions()).isEmpty();
    }
}
