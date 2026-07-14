package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.support.NormalizationRejectionClassifier;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NormalizationRejectionClassifierTest {
    private final NormalizationRejectionClassifier classifier = new NormalizationRejectionClassifier();

    @Test void distinguishesMissingEndpointTargetAndUnsupportedRule() {
        SourceTrace trace = new SourceTrace("Service.java", 1, 1);
        assertThat(classifier.classify(new ValidationCandidate("a", "SERVICE_HINT", "id", "rule", .5, trace)))
            .isEqualTo("NORMALIZATION_ENDPOINT_BINDING_FAILED");
        assertThat(classifier.classify(new ValidationCandidate("b", "SERVICE_HINT", null, "rule", .5, trace, "GET /x")))
            .isEqualTo("NORMALIZATION_TARGET_UNRESOLVED");
        assertThat(classifier.classify(new ValidationCandidate("c", "SERVICE_HINT", "id", "rule", .5, trace, "GET /x")))
            .isEqualTo("NORMALIZATION_RULE_UNSUPPORTED");
    }
}
