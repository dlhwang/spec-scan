package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.*;

public final class RequestBindingConditionAdapter {
    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output) {
        List<ExecutableCondition> conditions = new ArrayList<>(output.requestPreconditions());
        for (RequestBinding binding : endpoint.requestBindings()) {
            String location = binding.targetLocation().name();
            String path = binding.targetLocation() == BindingLocation.BODY ? "$" : "$." + binding.parameterName();
            if (binding.isRequired()) conditions.add(condition(location, path, "REQUIRED", List.of("true"),
                "request binding required flag", "REQUEST_BINDING_REQUIRED", binding.sourceTrace()));
            if (!binding.enumValues().isEmpty()) conditions.add(condition(location, path, "IN",
                binding.enumValues(), "request binding enum values", "REQUEST_BINDING_ENUM", binding.sourceTrace()));
        }
        return new EndpointRuleOutput(output.endpointPath(), deduplicate(conditions), output.responseAssertions(),
            output.excludedBusinessRules(), output.diagnostics());
    }

    private ExecutableCondition condition(String location, String path, String operator, List<String> expected,
                                          String expectedSource, String ruleId, SourceTrace trace) {
        SourceTrace source = trace == null ? new SourceTrace("unknown", 1, 1) : trace;
        int startLine = Math.max(1, source.startLine());
        int endLine = Math.max(startLine, source.endLine());
        String file = source.fileRelativePath() == null || source.fileRelativePath().isBlank()
            ? "unknown" : source.fileRelativePath();
        EvidenceRef evidence = new EvidenceRef(ruleId + ":" + startLine, file,
            startLine, 1, endLine, 1,
            EvidenceRole.INPUT_ORIGIN, expectedSource);
        return new ExecutableCondition(location, path, operator, expected, expectedSource, ruleId, 1.0,
            List.of(evidence));
    }

    private List<ExecutableCondition> deduplicate(List<ExecutableCondition> values) {
        Map<String, ExecutableCondition> result = new LinkedHashMap<>();
        for (ExecutableCondition value : values) result.putIfAbsent(value.targetLocation() + "|"
            + value.targetPath() + "|" + value.operator() + "|" + value.expectedValues(), value);
        return List.copyOf(result.values());
    }
}
