package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

public final class CandidateToOutputAdapter {
    private static final Set<String> EXCLUDED_RULE_ALLOWLIST = Set.of(
        "SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW", "JDK_OPTIONAL_LOOKUP_FAILURE",
        "SPRING_SECURITY_PASSWORD_MATCH_FAILURE", "JAVA_AUTHORIZATION_GUARD_CALL");
    public EndpointRuleOutput adapt(ApiEndpoint endpoint, List<BusinessRuleCandidate> candidates) {
        List<ExecutableCondition> preconditions = new ArrayList<>();
        List<ExcludedBusinessRule> excluded = new ArrayList<>();
        List<CandidateOutputDiagnostic> diagnostics = new ArrayList<>();
        for (BusinessRuleCandidate candidate : candidates.stream()
                .sorted(Comparator.comparing(BusinessRuleCandidate::candidateId)).toList()) {
            if (candidate.extractionStatus() == ExtractionStatus.UNSUPPORTED) {
                diagnostics.add(diagnostic("STRUCTURE_UNSUPPORTED", "Candidate structure is unsupported", candidate));
                continue;
            }
            if (candidate.semanticStatus() != SemanticStatus.RESOLVED) {
                diagnostics.add(diagnostic("SEMANTIC_UNRESOLVED", "Candidate semantic meaning is unresolved", candidate));
                continue;
            }
            NormalizedConstraint constraint = candidate.constraint();
            if (constraint == null) {
                addExcluded(candidate, null, "CONTROL_FLOW_ONLY", excluded, diagnostics);
            } else if (constraint.kind() == ConstraintKind.RUNTIME_DEPENDENT) {
                addExcluded(candidate, constraint, "RUNTIME_DEPENDENT", excluded, diagnostics);
            } else if (candidate.targetStatus() != TargetResolutionStatus.RESOLVED) {
                addExcluded(candidate, constraint, "TARGET_UNRESOLVED", excluded, diagnostics);
            } else if (constraint.kind() == ConstraintKind.CONTROL_FLOW_ONLY
                    || constraint.kind() == ConstraintKind.INPUT_TO_DOMAIN) {
                addExcluded(candidate, constraint, "EXTERNAL_STATE_REQUIRED", excluded, diagnostics);
            } else {
                addExecutable(endpoint, candidate, constraint, preconditions, excluded, diagnostics);
            }
        }
        return new EndpointRuleOutput(endpoint.path(), deduplicate(preconditions), List.of(),
            deduplicateExcluded(excluded), diagnostics);
    }

    private void addExecutable(ApiEndpoint endpoint, BusinessRuleCandidate candidate, NormalizedConstraint constraint,
                               List<ExecutableCondition> preconditions, List<ExcludedBusinessRule> excluded,
                               List<CandidateOutputDiagnostic> diagnostics) {
        ResolvedRequestTarget target = resolveRequestTarget(endpoint, constraint.targetPath());
        if (target == null) {
            excluded.add(excluded(candidate, constraint, "TARGET_NOT_REQUEST_BOUND"));
        } else if (!isExecutable(constraint)) {
            diagnostics.add(diagnostic("OUTPUT_SCHEMA_UNSUPPORTED",
                "Executable request condition requires operator and expected values", candidate));
        } else {
            preconditions.add(new ExecutableCondition(target.location(), target.path(), constraint.operator(),
                constraint.expectedValues(), constraint.expectedSource(), candidate.ruleId(), candidate.confidence(),
                candidate.evidence()));
        }
    }

    private void addExcluded(BusinessRuleCandidate candidate, NormalizedConstraint constraint, String reason,
                             List<ExcludedBusinessRule> excluded,
                             List<CandidateOutputDiagnostic> diagnostics) {
        if (!EXCLUDED_RULE_ALLOWLIST.contains(candidate.ruleId())) {
            diagnostics.add(diagnostic("EXCLUDED_RULE_NOT_ALLOWLISTED",
                "Rule has no registered excluded-business-rule template", candidate));
            return;
        }
        boolean predicate = candidate.evidence().stream().anyMatch(ref -> ref.role() == EvidenceRole.PREDICATE);
        boolean outcome = candidate.evidence().stream().anyMatch(ref -> ref.role() == EvidenceRole.FAILURE_OUTCOME);
        if (!predicate || !outcome) {
            diagnostics.add(diagnostic("EXCLUDED_RULE_EVIDENCE_INCOMPLETE",
                "Excluded rule requires predicate and failure outcome facts", candidate));
            return;
        }
        excluded.add(excluded(candidate, constraint, reason));
    }

    private boolean isExecutable(NormalizedConstraint constraint) {
        try {
            CanonicalOperator operator = CanonicalOperator.parse(constraint.operator());
            return !operator.requiresExpectedValue() || !constraint.expectedValues().isEmpty();
        } catch (IllegalArgumentException ignored) {
            return false;
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

    private ExcludedBusinessRule excluded(BusinessRuleCandidate candidate, NormalizedConstraint constraint,
                                          String reason) {
        boolean nonExecutable = constraint != null && (constraint.kind() == ConstraintKind.RUNTIME_DEPENDENT
            || constraint.kind() == ConstraintKind.CONTROL_FLOW_ONLY
            || constraint.kind() == ConstraintKind.INPUT_TO_DOMAIN);
        return new ExcludedBusinessRule(candidate.ruleId(), candidate.category(),
            constraint == null ? ConstraintKind.CONTROL_FLOW_ONLY : constraint.kind(), candidate.extractionStatus(),
            candidate.semanticStatus(), candidate.targetStatus(), reason,
            constraint == null ? null : constraint.targetPath(),
            constraint == null || nonExecutable ? null : constraint.operator(),
            constraint == null || nonExecutable ? List.of() : constraint.expectedValues(),
            constraint == null ? null : constraint.expectedSource(), candidate.confidence(), candidate.evidence());
    }
    private CandidateOutputDiagnostic diagnostic(String code, String message, BusinessRuleCandidate candidate) {
        return new CandidateOutputDiagnostic(code, message, candidate.candidateId(), candidate.ruleId());
    }
    private List<ExecutableCondition> deduplicate(List<ExecutableCondition> values) {
        Map<String, ExecutableCondition> result = new LinkedHashMap<>();
        for (ExecutableCondition value : values) result.putIfAbsent(value.targetLocation() + "|" + value.targetPath()
            + "|" + value.operator() + "|" + value.expectedValues() + "|" + value.ruleId(), value);
        return List.copyOf(result.values());
    }
    private List<ExcludedBusinessRule> deduplicateExcluded(List<ExcludedBusinessRule> values) {
        Map<String, ExcludedBusinessRule> result = new LinkedHashMap<>();
        for (ExcludedBusinessRule value : values) result.putIfAbsent(value.ruleId() + "|" + value.reasonCode()
            + "|" + value.targetPath(), value);
        return List.copyOf(result.values());
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private record ResolvedRequestTarget(String location, String path) {}
}
