package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.candidate.NormalizedConstraint;
import io.atworks.specscan.analysis.domain.output.CanonicalOperator;

final class OutputConstraintSupport {
    private OutputConstraintSupport() {}

    static boolean isExecutable(NormalizedConstraint constraint) {
        try {
            CanonicalOperator operator = CanonicalOperator.parse(constraint.operator());
            return !operator.requiresExpectedValue() || !constraint.expectedValues().isEmpty();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
