package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

public final class CandidateToOutputAdapter {
    private final RequestPreconditionOutputAdapter requestAdapter = new RequestPreconditionOutputAdapter();
    private final ResponseAssertionOutputAdapter responseAdapter = new ResponseAssertionOutputAdapter();
    private final ExcludedBusinessRuleOutputAdapter excludedAdapter = new ExcludedBusinessRuleOutputAdapter();

    public EndpointRuleOutput adapt(ApiEndpoint endpoint, List<BusinessRuleCandidate> candidates) {
        List<ExecutableCondition> preconditions = new ArrayList<>();
        List<ExecutableCondition> assertions = new ArrayList<>();
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
            RuleEffect effect = candidate.effect();
            if ("JAVA_DELEGATED_GUARD".equals(candidate.ruleId())) {
                effect = RuleEffect.BUSINESS_RESTRICTION;
            }
            switch (effect) {
                case REQUEST_REQUIREMENT -> requestAdapter.add(endpoint, candidate, constraint, preconditions, diagnostics);
                case RESPONSE_GUARANTEE -> responseAdapter.add(candidate, constraint, assertions, diagnostics);
                case BUSINESS_RESTRICTION -> excludedAdapter.add(candidate, constraint, excluded, diagnostics);
            }
        }
        List<ExcludedBusinessRule> deduplicatedExcluded = deduplicateExcluded(excluded);
        List<ExcludedBusinessRule> prerequisites = deduplicatedExcluded.stream()
            .filter(rule -> "EXTERNAL_STATE_REQUIRED".equals(rule.reasonCode())).toList();
        return new EndpointRuleOutput(endpoint.path(), deduplicate(preconditions), deduplicate(assertions),
            prerequisites, deduplicatedExcluded, diagnostics);
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
            + "|" + value.targetPath() + "|" + value.operator() + "|" + value.expectedValues()
            + "|" + value.expectedSource(), value);
        return List.copyOf(result.values());
    }
}
