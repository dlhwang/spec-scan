package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.candidate.EvidenceRef;
import io.atworks.specscan.analysis.domain.candidate.EvidenceRole;
import io.atworks.specscan.analysis.domain.output.CanonicalOperator;
import io.atworks.specscan.analysis.domain.output.ExecutableCondition;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CanonicalOperatorTest {
    @Test void acceptsOnlyCanonicalValuesAndMechanicalAliases() {
        assertThat(CanonicalOperator.parse("GREATER_THAN")).isEqualTo(CanonicalOperator.GT);
        assertThat(CanonicalOperator.parse("EQUALS")).isEqualTo(CanonicalOperator.EQ);
        assertThatThrownBy(() -> CanonicalOperator.parse("REQUIRED"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsupported operator");
        assertThatThrownBy(() -> CanonicalOperator.parse("EXISTS_IN_REPOSITORY"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void executableConditionStoresCanonicalOperatorAndAllowsUnaryWithoutExpected() {
        ExecutableCondition unary = condition("NOT_NULL", List.of());
        ExecutableCondition comparison = condition("LESS_THAN_OR_EQUAL", List.of("10"));
        assertThat(unary.operator()).isEqualTo("NOT_NULL");
        assertThat(comparison.operator()).isEqualTo("LTE");
        assertThatThrownBy(() -> condition("GT", List.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedValues");
    }

    private ExecutableCondition condition(String operator, List<String> expected) {
        EvidenceRef evidence = new EvidenceRef("node", "src/Test.java", 1, 1, 1, 2,
            EvidenceRole.PREDICATE, "evidence");
        return new ExecutableCondition("BODY", "$.value", operator, expected, null,
            "TEST_RULE", 1.0, List.of(evidence));
    }
}
