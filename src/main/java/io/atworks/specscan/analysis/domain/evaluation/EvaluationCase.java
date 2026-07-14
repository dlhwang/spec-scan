package io.atworks.specscan.analysis.domain.evaluation;

import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCandidate;
import java.util.*;

public record EvaluationCase(GoldenRuleLabel label, List<BusinessRuleCandidate> observed) {
    public EvaluationCase {
        Objects.requireNonNull(label); observed = List.copyOf(Objects.requireNonNull(observed));
    }
}
