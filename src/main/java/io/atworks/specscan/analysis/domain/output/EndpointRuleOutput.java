package io.atworks.specscan.analysis.domain.output;

import java.util.*;

public record EndpointRuleOutput(String endpointPath, List<ExecutableCondition> requestPreconditions,
                                 List<ExecutableCondition> responseAssertions,
                                 List<ExcludedBusinessRule> excludedBusinessRules,
                                 List<CandidateOutputDiagnostic> diagnostics) {
    public EndpointRuleOutput {
        if (endpointPath == null || endpointPath.isBlank()) throw new IllegalArgumentException("endpointPath is required");
        requestPreconditions = List.copyOf(Objects.requireNonNull(requestPreconditions));
        responseAssertions = List.copyOf(Objects.requireNonNull(responseAssertions));
        excludedBusinessRules = List.copyOf(Objects.requireNonNull(excludedBusinessRules));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics));
    }
    public static EndpointRuleOutput empty(String endpointPath) {
        return new EndpointRuleOutput(endpointPath, List.of(), List.of(), List.of(), List.of());
    }
}
