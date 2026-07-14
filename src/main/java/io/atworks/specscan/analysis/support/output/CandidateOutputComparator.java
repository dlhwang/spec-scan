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
        for (ExecutableCondition condition : current.requestPreconditions())
            currentByTarget.put(targetKey("REQUEST", condition.targetLocation(), condition.targetPath()), condition);
        for (ExecutableCondition condition : current.responseAssertions())
            currentByTarget.put(targetKey("RESPONSE", condition.targetLocation(), condition.targetPath()), condition);
        Set<String> keys = new TreeSet<>(); keys.addAll(legacyByTarget.keySet()); keys.addAll(currentByTarget.keySet());
        List<MigrationDifference> differences = new ArrayList<>();
        for (String key : keys) {
            ApiCondition old = legacyByTarget.get(key); ExecutableCondition next = currentByTarget.get(key);
            if (old == null) differences.add(new MigrationDifference(MigrationDifferenceKind.NEW_ONLY, key, next.ruleId()));
            else if (next == null) differences.add(new MigrationDifference(MigrationDifferenceKind.LEGACY_ONLY, key, old.operator()));
            else if (equivalent(old, next)) differences.add(new MigrationDifference(MigrationDifferenceKind.EQUIVALENT, key, next.ruleId()));
            else differences.add(new MigrationDifference(MigrationDifferenceKind.CONFLICTING, key,
                old.operator() + "/" + old.expected() + " != " + next.operator() + "/" + next.expectedValues()));
        }
        for (CandidateOutputDiagnostic diagnostic : current.diagnostics())
            differences.add(new MigrationDifference(MigrationDifferenceKind.UNRESOLVED_BY_NEW_ENGINE,
                diagnostic.candidateId(), diagnostic.code()));
        for (ApiCondition condition : unresolvedScope) differences.add(new MigrationDifference(
            MigrationDifferenceKind.LEGACY_SCOPE_UNRESOLVED, targetKey(condition),
            condition.sourceTrace().fileRelativePath()));
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
    private boolean equivalent(ApiCondition legacy, ExecutableCondition current) {
        return Objects.equals(legacy.operator(), current.operator()) && current.expectedValues().size() == 1
            && Objects.equals(legacy.expected(), current.expectedValues().get(0));
    }
    private String targetKey(ApiCondition condition) {
        return targetKey(condition.conditionType() == ConditionType.ASSERTION ? "RESPONSE" : "REQUEST",
            condition.targetLocation().name(), condition.targetPath());
    }
    private String targetKey(String phase, String location, String path) {
        return phase + "|" + location + "|" + path;
    }
}
