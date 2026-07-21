package io.atworks.apiintelligence.domain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiDomainTest {

    @Test
    void deterministicIdsAndNormalizedApi() {
        String id = ApiIdGenerator.generate("get", "//orders/", "a.Controller", "find()");
        assertThat(id).isEqualTo(
            ApiIdGenerator.generate("GET", "/orders", "a.Controller", "find()")).hasSize(64);
        DiscoveredApi api = new DiscoveredApi(id, "get", "//orders/", "a.Controller", "find()",
            List.of("z", "a", "a"), "Order", new SourceLocation("src\\A.java", 1, 1, 2, 1));
        assertThat(api.path()).isEqualTo("/orders");
        assertThat(api.requestBindings()).containsExactly("a", "z");
    }

    @Test
    void rejectsMismatchedId() {
        assertThatThrownBy(() -> new DiscoveredApi("bad", "GET", "/x", "C", "m()", List.of(), "R",
            new SourceLocation("A.java", 1, 1, 1, 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
