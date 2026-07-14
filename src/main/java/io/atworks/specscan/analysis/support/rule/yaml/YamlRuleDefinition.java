package io.atworks.specscan.analysis.support.rule.yaml;

import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCategory;
import io.atworks.specscan.analysis.domain.candidate.PredicateType;

public record YamlRuleDefinition(String id, PredicateType predicateType, String methodName,
                                 String resolvedSignatureContains, FailureOutcome failureOutcome,
                                 BusinessRuleCategory category) {
    public enum FailureOutcome { THEN, ELSE, ANY }
}
