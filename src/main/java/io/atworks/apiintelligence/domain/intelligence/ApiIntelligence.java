package io.atworks.apiintelligence.domain.intelligence;

import java.util.List;

public record ApiIntelligence(
    String apiId,
    List<IntelligenceItem> preConditions,
    List<IntelligenceItem> responseAssertions,
    List<IntelligenceItem> businessRules,
    List<IntelligenceItem> exceptions,
    List<IntelligenceItem> additionalAnalysis
) {

    public ApiIntelligence {
        if (apiId == null || apiId.isBlank()) {
            throw new IllegalArgumentException("apiId is blank");
        }
        preConditions = copy(preConditions);
        responseAssertions = copy(responseAssertions);
        businessRules = copy(businessRules);
        exceptions = copy(exceptions);
        additionalAnalysis = copy(additionalAnalysis);
    }

    private static List<IntelligenceItem> copy(List<IntelligenceItem> v) {
        return v == null ? List.of() : List.copyOf(v);
    }
}
