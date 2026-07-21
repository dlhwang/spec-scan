package io.atworks.apiintelligence.domain.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class IntelligenceDomainTest {

    @Test
    void validatesItem() {
        assertThatThrownBy(
            () -> new IntelligenceItem("d", "c", List.of("e"), Double.NaN)).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntelligenceItem("d", "c", List.of(), .5)).isInstanceOf(
            IllegalArgumentException.class);
        assertThat(new IntelligenceItem("d", "c", List.of("z", "a", "a"),
            1).evidenceIds()).containsExactly("a", "z");
    }

    @Test
    void nullListsAreEmpty() {
        assertThat(
            new ApiIntelligence("api", null, null, null, null, null).preConditions()).isEmpty();
    }
}
