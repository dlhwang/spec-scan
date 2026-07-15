package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

final class RequestPreconditionOutputAdapter {
    void add(ApiEndpoint endpoint, BusinessRuleCandidate candidate, NormalizedConstraint constraint,
             List<ExecutableCondition> preconditions, List<CandidateOutputDiagnostic> diagnostics) {
        if (constraint == null) {
            diagnostics.add(diagnostic("REQUEST_CONSTRAINT_MISSING",
                "Request requirement requires an executable constraint", candidate));
            return;
        }
        ResolvedRequestTarget target = resolveRequestTarget(endpoint, constraint.targetPath());
        if (target == null) {
            diagnostics.add(diagnostic("TARGET_NOT_REQUEST_BOUND",
                "Request requirement target is not bound to the endpoint request", candidate));
        } else if (!OutputConstraintSupport.isExecutable(constraint)) {
            diagnostics.add(diagnostic("OUTPUT_SCHEMA_UNSUPPORTED",
                "Executable request condition requires operator and expected values", candidate));
        } else {
            preconditions.add(new ExecutableCondition(target.location(), target.path(), constraint.operator(),
                constraint.expectedValues(), constraint.expectedSource(), candidate.ruleId(), candidate.confidence(),
                candidate.evidence()));
        }
    }

    private ResolvedRequestTarget resolveRequestTarget(ApiEndpoint endpoint, String rawPath) {
        if (blank(rawPath)) return null;
        String normalized = rawPath.startsWith("$.") ? rawPath.substring(2) : rawPath;
        for (RequestBinding binding : endpoint.requestBindings()) {
            String name = binding.parameterName();
            if (normalized.equals(name) || normalized.startsWith(name + ".")) {
                String suffix = normalized.equals(name) ? "" : normalized.substring(name.length() + 1);
                String path = binding.targetLocation() == BindingLocation.BODY
                    ? (suffix.isBlank() ? "$" : "$." + suffix) : "$." + name;
                return new ResolvedRequestTarget(binding.targetLocation().name(), path);
            }
        }
        RequestBinding body = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() == BindingLocation.BODY).findFirst().orElse(null);
        return body == null ? null : new ResolvedRequestTarget("BODY", "$." + normalized);
    }

    private CandidateOutputDiagnostic diagnostic(String code, String message, BusinessRuleCandidate candidate) {
        return new CandidateOutputDiagnostic(code, message, candidate.candidateId(), candidate.ruleId());
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private record ResolvedRequestTarget(String location, String path) {}
}
