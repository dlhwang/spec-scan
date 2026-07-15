package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.*;

public final class StructuralConditionAdapter {
    private static final Set<String> SUPPORTED = Set.of(
        "REQUIRED", "NOT_NULL", "NOT_BLANK", "SIZE", "MIN", "MAX", "PATTERN", "IN", "EMAIL");

    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output,
                                      List<ApiCondition> normalizedConditions) {
        List<ExecutableCondition> result = new ArrayList<>(output.requestPreconditions());
        for (ApiCondition condition : normalizedConditions) {
            if (condition.conditionType() == ConditionType.ASSERTION || !SUPPORTED.contains(condition.operator())
                    || !belongsTo(endpoint, condition) || condition.sourceTrace() == null) continue;
            String location = resolveLocation(endpoint, condition);
            if (location == null) continue;
            result.add(toExecutable(condition, location));
        }
        return new EndpointRuleOutput(output.endpointPath(), deduplicate(result), output.responseAssertions(),
            output.excludedBusinessRules(), output.diagnostics());
    }

    private boolean belongsTo(ApiEndpoint endpoint, ApiCondition condition) {
        if (condition.endpointPath() != null) return endpoint.path().equals(condition.endpointPath());
        String source = file(condition.sourceTrace());
        return endpoint.requestBindings().stream().anyMatch(binding -> {
            SourceTrace trace = binding.sourceTrace();
            if (trace != null && source.equals(file(trace))) return true;
            if (binding.targetLocation() != BindingLocation.BODY) return false;
            String type = binding.type();
            int generic = type.indexOf('<');
            if (generic >= 0) type = type.substring(0, generic);
            int separator = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
            String simpleName = separator < 0 ? type : type.substring(separator + 1);
            return source.endsWith("/" + simpleName + ".java") || source.equals(simpleName + ".java");
        });
    }

    private String resolveLocation(ApiEndpoint endpoint, ApiCondition condition) {
        if (condition.targetLocation() != null && condition.targetLocation() != ConditionLocation.UNKNOWN) {
            return condition.targetLocation().name();
        }
        return endpoint.requestBindings().stream().filter(binding -> binding.targetLocation() == BindingLocation.BODY)
            .findFirst().map(binding -> "BODY").orElse(null);
    }

    private ExecutableCondition toExecutable(ApiCondition condition, String location) {
        SourceTrace trace = condition.sourceTrace();
        int start = Math.max(1, trace.startLine());
        int end = Math.max(start, trace.endLine());
        String path = condition.targetPath().startsWith("$") ? condition.targetPath() : "$." + condition.targetPath();
        EvidenceRef evidence = new EvidenceRef("structural:" + condition.operator() + ":" + start,
            file(trace), start, 1, end, 1, EvidenceRole.INPUT_ORIGIN,
            condition.evidence() == null ? condition.operator() : condition.evidence());
        List<String> expected = condition.expected() == null ? List.of() : List.of(condition.expected());
        return new ExecutableCondition(location, path, condition.operator(), expected,
            "source annotation or DTO constraint", "STRUCTURAL_" + condition.operator(),
            condition.confidence(), List.of(evidence));
    }

    private List<ExecutableCondition> deduplicate(List<ExecutableCondition> values) {
        Map<String, ExecutableCondition> result = new LinkedHashMap<>();
        for (ExecutableCondition value : values) result.putIfAbsent(value.targetLocation() + "|" + value.targetPath()
            + "|" + value.operator() + "|" + value.expectedValues(), value);
        return List.copyOf(result.values());
    }

    private String file(SourceTrace trace) {
        String value = trace.fileRelativePath();
        return value == null || value.isBlank() ? "unknown" : value.replace('\\', '/');
    }
}
