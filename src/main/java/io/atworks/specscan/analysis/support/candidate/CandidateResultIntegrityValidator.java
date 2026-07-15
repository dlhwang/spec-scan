package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CandidateResultIntegrityValidator {
    private final CandidateInvariantValidator invariantValidator = new CandidateInvariantValidator();

    public void validate(CandidateResolutionResult result, FactCodeGraph graph) {
        if (!result.graphId().equals(graph.graphId())) throw new IllegalArgumentException("graph id mismatch");
        Set<String> factIds = new HashSet<>();
        graph.nodes().forEach(node -> factIds.add(node.id()));
        Set<String> reachableFactIds = reachableFromApiRoot(graph);
        Set<String> predicateIds = uniquePredicateIds(result, factIds, reachableFactIds);
        Set<String> ruleIds = new HashSet<>();
        for (BusinessRuleCandidate rule : result.businessRules()) {
            invariantValidator.validate(rule);
            if (!ruleIds.add(rule.candidateId())) throw invalid("DUPLICATE_CANDIDATE_ID");
            if (!predicateIds.contains(rule.predicateCandidateId())) throw invalid("DANGLING_PREDICATE_REFERENCE");
            validateEvidence(rule.evidence(), factIds, reachableFactIds);
        }
    }

    private Set<String> uniquePredicateIds(CandidateResolutionResult result, Set<String> factIds,
                                           Set<String> reachableFactIds) {
        Set<String> ids = new HashSet<>();
        for (PredicateCandidate predicate : result.predicates()) {
            invariantValidator.validate(predicate);
            if (!ids.add(predicate.candidateId())) throw invalid("DUPLICATE_CANDIDATE_ID");
            if (!predicate.graphId().equals(result.graphId()) || !factIds.contains(predicate.conditionNodeId())) {
                throw invalid("DANGLING_PREDICATE_REFERENCE");
            }
            validateEvidence(predicate.evidence(), factIds, reachableFactIds);
        }
        return ids;
    }
    private void validateEvidence(Iterable<EvidenceRef> evidence, Set<String> factIds,
                                  Set<String> reachableFactIds) {
        for (EvidenceRef ref : evidence) {
            if (!factIds.contains(ref.nodeId())) throw invalid("DANGLING_EVIDENCE_REFERENCE");
            if (!reachableFactIds.contains(ref.nodeId())) throw invalid("UNREACHABLE_EVIDENCE_REFERENCE");
        }
    }
    private Set<String> reachableFromApiRoot(FactCodeGraph graph) {
        Map<String, List<String>> adjacency = new HashMap<>();
        graph.edges().forEach(edge -> adjacency.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>())
            .add(edge.targetNodeId()));
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(graph.apiMethodNodeId());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            adjacency.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return visited;
    }
    private IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
