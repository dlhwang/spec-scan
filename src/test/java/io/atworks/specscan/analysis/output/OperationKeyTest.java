package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.OperationKey;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OperationKeyTest {
    @Test void distinguishesMethodsThatShareAPath() {
        ApiEndpoint get = endpoint("get", "GET", "/properties/");
        ApiEndpoint post = endpoint("post", "post", "/properties");
        assertThat(OperationKey.of(get).externalKey()).isEqualTo("GET /properties");
        assertThat(OperationKey.of(post).externalKey()).isEqualTo("POST /properties");
        assertThat(OperationKey.of(get)).isNotEqualTo(OperationKey.of(post));
    }

    private ApiEndpoint endpoint(String operation, String method, String path) {
        SourceTrace trace = new SourceTrace("Controller.java", 1, 1);
        return new ApiEndpoint(method, path, "example.Controller", operation, List.of(),
            new ResponseBinding("void", trace), trace);
    }
}
