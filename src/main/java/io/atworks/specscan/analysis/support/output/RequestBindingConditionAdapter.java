package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.*;

public final class RequestBindingConditionAdapter {
    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output) {
        return augment(endpoint, output, List.of());
    }

    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output,
                                      List<ApiConditionDraft> annotationConditions) {
        List<ExecutableCondition> conditions = new ArrayList<>(output.requestPreconditions());
        List<CandidateOutputDiagnostic> diagnostics = new ArrayList<>(output.diagnostics());
        for (RequestBinding binding : endpoint.requestBindings()) {
            String location = binding.targetLocation().name();
            String path = binding.targetLocation() == BindingLocation.BODY ? "$" : "$." + binding.parameterName();
            if (binding.isRequired()) conditions.add(condition(location, path, "NOT_NULL", List.of(),
                "request binding required flag", "REQUEST_BINDING_REQUIRED", binding.sourceTrace()));
        }
        for (ApiConditionDraft draft : annotationConditions) {
            if (!belongsTo(endpoint, draft)) continue;
            RequestBinding binding = resolveBinding(endpoint, draft.targetPath());
            if (binding == null) continue;
            if (!supports(draft.operator())) {
                diagnostics.add(new CandidateOutputDiagnostic("REQUEST_ANNOTATION_UNSUPPORTED",
                    "Request annotation operator is not executable without an approved mapping",
                    "annotation:" + draft.targetPath() + ":" + draft.operator(), null));
                continue;
            }
            String location = binding.targetLocation().name();
            String path = resolvePath(binding, draft.targetPath());
            conditions.add(condition(location, path, draft.operator(), expected(draft),
                "source annotation or DTO constraint", "REQUEST_ANNOTATION_" + draft.operator(),
                draft.sourceTrace()));
        }
        return new EndpointRuleOutput(output.endpointPath(), deduplicate(conditions), output.responseAssertions(),
            output.excludedBusinessRules(), diagnostics);
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

    private boolean belongsTo(ApiEndpoint endpoint, ApiConditionDraft draft) {
        String source = file(draft.sourceTrace());
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

    private RequestBinding resolveBinding(ApiEndpoint endpoint, String targetPath) {
        String normalized = normalize(targetPath);
        for (RequestBinding binding : endpoint.requestBindings()) {
            String name = binding.parameterName();
            if (normalized.equals(name) || normalized.startsWith(name + ".")) return binding;
        }
        return endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() == BindingLocation.BODY).findFirst().orElse(null);
    }

    private String resolvePath(RequestBinding binding, String targetPath) {
        String normalized = normalize(targetPath);
        if (binding.targetLocation() != BindingLocation.BODY) return "$." + binding.parameterName();
        String name = binding.parameterName();
        if (normalized.equals(name)) return "$";
        if (normalized.startsWith(name + ".")) return "$." + normalized.substring(name.length() + 1);
        return "$." + normalized;
    }

    private boolean supports(String operator) {
        return "NOT_NULL".equals(operator) || "NOT_EMPTY".equals(operator);
    }

    private List<String> expected(ApiConditionDraft draft) {
        return draft.expected() == null || draft.expected().isBlank() ? List.of() : List.of(draft.expected());
    }

    private String normalize(String path) {
        return path == null ? "" : path.startsWith("$.") ? path.substring(2) : path;
    }

    private String file(SourceTrace trace) {
        if (trace == null || trace.fileRelativePath() == null || trace.fileRelativePath().isBlank()) return "unknown";
        return trace.fileRelativePath().replace('\\', '/');
    }
}
