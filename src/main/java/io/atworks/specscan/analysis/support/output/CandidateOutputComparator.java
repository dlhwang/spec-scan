package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

public final class CandidateOutputComparator {
    public CandidateOutputComparisonReport compare(ApiEndpoint endpoint, List<ApiCondition> legacy,
                                                   EndpointRuleOutput current) {
        String endpointPath = endpoint.path();
        Map<String, ApiCondition> legacyByTarget = new TreeMap<>();
        List<ApiCondition> unresolvedScope = new ArrayList<>();
        for (ApiCondition condition : legacy) {
            if (endpointPath.equals(condition.endpointPath()) || sourceBelongsToEndpoint(condition, endpoint)) {
                legacyByTarget.put(targetKey(condition), condition);
            } else if (condition.endpointPath() == null && condition.sourceTrace() != null) {
                unresolvedScope.add(condition);
            }
        }
        Map<String, ExecutableCondition> currentByTarget = new TreeMap<>();
        Set<String> runtimeKeys = new HashSet<>();
        for (ExecutableCondition condition : current.requestPreconditions())
            currentByTarget.put(targetKey("REQUEST", condition.targetLocation(), condition.targetPath(),
                condition.operator()), condition);
        for (ExecutableCondition condition : current.responseAssertions())
            currentByTarget.put(targetKey("RESPONSE", condition.targetLocation(), condition.targetPath(),
                condition.operator()), condition);
        for (ExcludedBusinessRule rule : current.excludedBusinessRules()) {
            String location = excludedLocation(endpoint, rule);
            if (location == null || rule.targetPath() == null || rule.operator() == null || rule.evidence().isEmpty()) continue;
            String path = rule.targetPath().startsWith("$") ? rule.targetPath() : "$." + rule.targetPath();
            String key = targetKey("REQUEST", location, path, rule.operator());
            currentByTarget.put(key, new ExecutableCondition(location, path, rule.operator(), rule.expectedValues(),
                rule.expectedSource(), rule.ruleId(), rule.confidence(), rule.evidence()));
            runtimeKeys.add(key);
        }
        Set<String> keys = new TreeSet<>(); keys.addAll(legacyByTarget.keySet()); keys.addAll(currentByTarget.keySet());
        List<MigrationDifference> differences = new ArrayList<>();
        for (String key : keys) {
            ApiCondition old = legacyByTarget.get(key); ExecutableCondition next = currentByTarget.get(key);
            if (old == null) differences.add(new MigrationDifference(MigrationDifferenceKind.NEW_ONLY, key, next.ruleId()));
            else if (next == null) differences.add(new MigrationDifference(MigrationDifferenceKind.LEGACY_ONLY, key, old.operator()));
            else if (equivalent(old, next, runtimeKeys.contains(key))) differences.add(new MigrationDifference(MigrationDifferenceKind.EQUIVALENT, key, next.ruleId()));
            else differences.add(new MigrationDifference(MigrationDifferenceKind.CONFLICTING, key,
                old.operator() + "/" + old.expected() + " != " + next.operator() + "/" + next.expectedValues()));
        }
        for (CandidateOutputDiagnostic diagnostic : current.diagnostics())
            differences.add(new MigrationDifference(MigrationDifferenceKind.UNRESOLVED_BY_NEW_ENGINE,
                diagnostic.candidateId(), diagnostic.code()));
        for (ApiCondition condition : unresolvedScope) differences.add(new MigrationDifference(
            MigrationDifferenceKind.LEGACY_SCOPE_UNRESOLVED, targetKey(condition),
            condition.sourceTrace().fileRelativePath()));
        if (differences.isEmpty()) differences.add(new MigrationDifference(
            MigrationDifferenceKind.NO_CONDITIONS_OBSERVED,
            OperationKey.of(endpoint).externalKey(), "Neither legacy nor new conditions were observed"));
        return new CandidateOutputComparisonReport(endpoint.httpMethod(), endpointPath,
            endpoint.controllerClass() + "#" + endpoint.controllerMethod(), differences);
    }
    private boolean sourceBelongsToEndpoint(ApiCondition condition, ApiEndpoint endpoint) {
        if (condition.endpointPath() != null || condition.sourceTrace() == null) return false;
        String source = normalizedFile(condition.sourceTrace().fileRelativePath());
        if (source.isBlank()) return false;
        if (endpoint.sourceTrace() != null && source.equals(normalizedFile(endpoint.sourceTrace().fileRelativePath()))) {
            return lineOverlaps(condition.sourceTrace(), endpoint.sourceTrace());
        }
        return endpoint.requestBindings().stream().map(RequestBinding::sourceTrace).filter(Objects::nonNull)
            .anyMatch(trace -> source.equals(normalizedFile(trace.fileRelativePath())));
    }
    private boolean lineOverlaps(io.atworks.specscan.ingestion.domain.SourceTrace left,
                                 io.atworks.specscan.ingestion.domain.SourceTrace right) {
        return left.startLine() <= right.endLine() && right.startLine() <= left.endLine();
    }
    private String normalizedFile(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
    private boolean equivalent(ApiCondition legacy, ExecutableCondition current, boolean runtimeDependent) {
        if (!Objects.equals(legacy.operator(), current.operator())) return false;
        if (runtimeDependent && current.expectedValues().isEmpty() && current.expectedSource() != null) return true;
        List<String> oldValues = legacy.expected() == null ? List.of()
            : Arrays.stream(legacy.expected().split(",")).map(String::trim).sorted().toList();
        return oldValues.equals(current.expectedValues().stream().map(String::trim).sorted().toList());
    }
    private String excludedLocation(ApiEndpoint endpoint, ExcludedBusinessRule rule) {
        if (rule.category() == io.atworks.specscan.analysis.domain.candidate.BusinessRuleCategory.STATE_PRECONDITION)
            return "RESOURCE";
        if (rule.category() == io.atworks.specscan.analysis.domain.candidate.BusinessRuleCategory.AUTHORIZATION)
            return "AUTH";
        String target = rule.targetPath() == null ? "" : rule.targetPath().replace("$.", "");
        return endpoint.requestBindings().stream().filter(binding -> binding.parameterName().equals(target))
            .map(binding -> binding.targetLocation().name()).findFirst().orElse(null);
    }
    private String targetKey(ApiCondition condition) {
        return targetKey(condition.conditionType() == ConditionType.ASSERTION ? "RESPONSE" : "REQUEST",
            condition.targetLocation().name(), condition.targetPath(), condition.operator());
    }
    private String targetKey(String phase, String location, String path) {
        return targetKey(phase, location, path, null);
    }
    private String targetKey(String phase, String location, String path, String operator) {
        return phase + "|" + location + "|" + path + "|" + Objects.toString(operator, "");
    }
}
