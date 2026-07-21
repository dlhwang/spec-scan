package io.atworks.apiintelligence.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceDomainTest {

    @Test
    void deterministicIdAndReferences() {
        SourceLocation l = new SourceLocation("src\\A.java", 3, 1, 5, 2);
        String id = EvidenceIdGenerator.generate("api", l, "CONDITION");
        Evidence e = new Evidence(id, "api", "CONDITION", l, "if (x)", List.of("z", "a", "a"));
        assertThat(id).hasSize(64);
        assertThat(e.graphNodeIds()).containsExactly("a", "z");
    }

    @Test
    void requiresNode() {
        SourceLocation l = new SourceLocation("A.java", 1, 1, 1, 1);
        assertThatThrownBy(
            () -> new Evidence(EvidenceIdGenerator.generate("api", l, "TYPE"), "api", "TYPE", l,
                "class A", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
