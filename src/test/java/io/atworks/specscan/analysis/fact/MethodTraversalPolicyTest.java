package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.fact.TraversalDecision;
import io.atworks.specscan.analysis.support.fact.DefaultMethodTraversalPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MethodTraversalPolicyTest {
    private final DefaultMethodTraversalPolicy policy = new DefaultMethodTraversalPolicy();

    @Test void visitsApplicationQueryMethodsEvenWhenTheirNameStartsWithGet() {
        assertThat(policy.decide("com.estate.api.service.PropertyService", "getProperty", true, "com.estate"))
            .isEqualTo(TraversalDecision.VISIT_BODY);
    }

    @Test void visitsSiblingBoundedContextsUnderTheApplicationRoot() {
        assertThat(policy.decide("com.myshop.order.command.StartShippingService", "startShipping", true,
            "com.myshop")).isEqualTo(TraversalDecision.VISIT_BODY);
    }

    @Test void stillRecordsRepositoryAndFrameworkCallsWithoutTraversingThem() {
        assertThat(policy.decide("com.estate.repository.PropertyRepository", "findById", true, "com.estate"))
            .isEqualTo(TraversalDecision.RECORD_CALL_ONLY);
        assertThat(policy.decide("java.util.Optional", "orElseThrow", false, "com.estate"))
            .isEqualTo(TraversalDecision.RECORD_CALL_ONLY);
    }
}
