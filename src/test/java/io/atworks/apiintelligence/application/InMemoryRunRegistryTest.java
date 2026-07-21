package io.atworks.apiintelligence.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atworks.apiintelligence.domain.run.AnalysisRunStatus;
import org.junit.jupiter.api.Test;

class InMemoryRunRegistryTest {

    @Test
    void rejectsDoubleTerminal() {
        var r = new InMemoryRunRegistry();
        r.create("r");
        r.transition("r", AnalysisRunStatus.COMPLETED);
        assertThatThrownBy(() -> r.transition("r", AnalysisRunStatus.FAILED)).isInstanceOf(
            IllegalStateException.class);
    }
}
