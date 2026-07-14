package io.atworks.specscan.analysis.domain.evaluation;

import java.util.*;

public record QualityGateResult(boolean passed, List<QualityGateViolation> violations) {
    public QualityGateResult { violations = List.copyOf(Objects.requireNonNull(violations)); }
}
