package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.support.output.CandidateOutputComparator;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateOutputComparatorTest {
    @Test void scopesPathlessLegacyConditionsByRequestBindingSource() {
        SourceTrace controller = new SourceTrace("src/CategoryController.java", 10, 20);
        SourceTrace categoryDto = new SourceTrace("src/CategoryRequest.java", 1, 30);
        SourceTrace orderDto = new SourceTrace("src/OrderRequest.java", 1, 30);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/categories", "CategoryController", "create",
            List.of(new RequestBinding("request", BindingLocation.BODY, "CategoryRequest", true,
                null, null, null, List.of(), categoryDto)), new ResponseBinding("void", controller), controller);
        ApiCondition category = condition("$.name", categoryDto);
        ApiCondition order = condition("$.shippingInfo", orderDto);

        CandidateOutputComparisonReport report = new CandidateOutputComparator().compare(endpoint,
            List.of(category, order), EndpointRuleOutput.empty(endpoint.path()));

        assertThat(report.differences()).filteredOn(d -> d.kind() == MigrationDifferenceKind.LEGACY_ONLY)
            .extracting(MigrationDifference::key).containsExactly("REQUEST|UNKNOWN|$.name");
        assertThat(report.differences()).filteredOn(d -> d.kind() == MigrationDifferenceKind.LEGACY_SCOPE_UNRESOLVED)
            .extracting(MigrationDifference::key).containsExactly("REQUEST|UNKNOWN|$.shippingInfo");
    }

    private ApiCondition condition(String path, SourceTrace trace) {
        return new ApiCondition(ConditionLocation.UNKNOWN, path, "REQUIRED", "true", null, 1.0, null, trace, null);
    }
}
