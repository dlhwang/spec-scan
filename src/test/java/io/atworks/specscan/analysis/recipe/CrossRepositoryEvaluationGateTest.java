package io.atworks.specscan.analysis.recipe;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.CrossRepositoryEvaluationGate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CrossRepositoryEvaluationGateTest {
    private final CrossRepositoryEvaluationGate gate = new CrossRepositoryEvaluationGate();

    @Test void goRequiresParityCoverageAndHoldoutEvidence() {
        assertThat(gate.evaluate(5, 30, 30, 30, 30, 0, 2).verdict()).isEqualTo(YamlEvaluationVerdict.GO);
    }

    @Test void fallbackOrCoverageGapIsPartial() {
        CrossRepositoryEvaluationReport report = gate.evaluate(5, 30, 24, 30, 30, 6, 1);
        assertThat(report.verdict()).isEqualTo(YamlEvaluationVerdict.PARTIAL);
        assertThat(report.diagnostics()).contains("YAML_COVERAGE_GAP", "FALLBACK_USED");
    }

    @Test void parityDiffIsNoGo() {
        assertThat(gate.evaluate(5, 30, 30, 30, 28, 0, 2).verdict()).isEqualTo(YamlEvaluationVerdict.NO_GO);
    }
}
