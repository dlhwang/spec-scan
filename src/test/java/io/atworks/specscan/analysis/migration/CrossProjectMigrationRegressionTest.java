package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.support.output.*;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CrossProjectMigrationRegressionTest {
    @Test void realEstateOperationsRemainDistinctAndProduceEvidenceBackedOutputs() {
        SourceTrace trace = new SourceTrace("src/PropertyController.java", 1, 20);
        List<ApiEndpoint> endpoints = List.of(
            endpoint("POST", "/api/auth/login", "login", trace, null),
            endpoint("GET", "/api/estate/properties", "getProperties", trace, null),
            endpoint("GET", "/api/estate/properties/{propertyId}", "getProperty", trace, null),
            endpoint("POST", "/api/estate/properties", "post", trace, 201),
            endpoint("PUT", "/api/estate/properties/{propertyId}", "put", trace, null),
            endpoint("DELETE", "/api/estate/properties/{propertyId}", "remove", trace, 204));

        assertThat(endpoints).extracting(endpoint -> OperationKey.of(endpoint).externalKey()).doesNotHaveDuplicates();
        EndpointRuleOutput post = output(endpoints.get(3));
        assertThat(post.requestPreconditions()).isNotEmpty();
        assertThat(post.responseAssertions()).singleElement()
            .extracting(ExecutableCondition::expectedValues).isEqualTo(List.of("201"));
    }

    @Test void dddStartOrderDtoConditionDoesNotLeakIntoCategoryEndpoint() {
        SourceTrace controller = new SourceTrace("src/CategoryController.java", 1, 20);
        SourceTrace categoryDto = new SourceTrace("src/CategoryRequest.java", 1, 20);
        SourceTrace orderDto = new SourceTrace("src/OrderRequest.java", 1, 20);
        ApiEndpoint categories = new ApiEndpoint("POST", "/categories", "CategoryController", "create",
            List.of(new RequestBinding("request", BindingLocation.BODY, "CategoryRequest", true,
                null, null, null, List.of(), categoryDto)), new ResponseBinding("void", controller), controller);
        ApiCondition leakedOrderCondition = new ApiCondition(ConditionLocation.UNKNOWN, "$.shippingInfo",
            "REQUIRED", "true", null, 1, null, orderDto, null);

        CandidateOutputComparisonReport report = new CandidateOutputComparator().compare(categories,
            List.of(leakedOrderCondition), EndpointRuleOutput.empty(categories.path()));

        assertThat(report.differences()).noneMatch(difference -> difference.kind() == MigrationDifferenceKind.LEGACY_ONLY);
        assertThat(report.differences()).anyMatch(difference -> difference.kind() == MigrationDifferenceKind.LEGACY_SCOPE_UNRESOLVED);
    }

    private EndpointRuleOutput output(ApiEndpoint endpoint) {
        EndpointRuleOutput request = new RequestBindingConditionAdapter().augment(endpoint,
            EndpointRuleOutput.empty(endpoint.path()));
        return new ResponseMetadataAdapter().augment(endpoint, request);
    }

    private ApiEndpoint endpoint(String method, String path, String operation, SourceTrace trace, Integer status) {
        RequestBinding request = new RequestBinding("request", BindingLocation.BODY, "PropertyRequest", true,
            null, null, null, List.of(), trace);
        ResponseBinding response = new ResponseBinding("Property", trace, status,
            status == null ? null : "@ResponseStatus");
        return new ApiEndpoint(method, path, "PropertyController", operation, List.of(request), response, trace);
    }
}
