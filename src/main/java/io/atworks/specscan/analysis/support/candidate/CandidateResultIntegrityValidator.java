package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.HashSet;
import java.util.Set;

public final class CandidateResultIntegrityValidator {
    private final CandidateInvariantValidator invariantValidator = new CandidateInvariantValidator();

    public void validate(CandidateResolutionResult result, FactCodeGraph graph) {
        if (!result.graphId().equals(graph.graphId())) throw new IllegalArgumentException("graph id mismatch");
        Set<String> factIds = new HashSet<>();
        graph.nodes().forEach(node -> factIds.add(node.id()));
        Set<String> predicateIds = uniquePredicateIds(result, factIds);
        Set<String> ruleIds = new HashSet<>();
        for (BusinessRuleCandidate rule : result.businessRules()) {
            invariantValidator.validate(rule);
            if (!ruleIds.add(rule.candidateId())) throw invalid("DUPLICATE_CANDIDATE_ID");
            if (!predicateIds.contains(rule.predicateCandidateId())) throw invalid("DANGLING_PREDICATE_REFERENCE");
            validateEvidence(rule.evidence(), factIds);
        }
    }

    private Set<String> uniquePredicateIds(CandidateResolutionResult result, Set<String> factIds) {
        Set<String> ids = new HashSet<>();
        for (PredicateCandidate predicate : result.predicates()) {
            invariantValidator.validate(predicate);
            if (!ids.add(predicate.candidateId())) throw invalid("DUPLICATE_CANDIDATE_ID");
            if (!predicate.graphId().equals(result.graphId()) || !factIds.contains(predicate.conditionNodeId())) {
                throw invalid("DANGLING_PREDICATE_REFERENCE");
            }
            validateEvidence(predicate.evidence(), factIds);
        }
        return ids;
    }
    private void validateEvidence(Iterable<EvidenceRef> evidence, Set<String> factIds) {
        for (EvidenceRef ref : evidence) if (!factIds.contains(ref.nodeId())) {
            throw invalid("DANGLING_EVIDENCE_REFERENCE");
        }
    }
    private IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
