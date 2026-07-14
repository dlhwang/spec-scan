package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

public final class CandidateOutputComparator {
    public CandidateOutputComparisonReport compare(ApiEndpoint endpoint, List<ApiCondition> legacy,
                                                   EndpointRuleOutput current) {
        String endpointPath = endpoint.path();
        Map<String, ApiCondition> legacyByTarget = new TreeMap<>();
        for (ApiCondition condition : legacy) if (condition.endpointPath() == null
                || endpointPath.equals(condition.endpointPath())) legacyByTarget.put(targetKey(condition), condition);
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
        return new CandidateOutputComparisonReport(endpoint.httpMethod(), endpointPath,
            endpoint.controllerClass() + "#" + endpoint.controllerMethod(), differences);
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
