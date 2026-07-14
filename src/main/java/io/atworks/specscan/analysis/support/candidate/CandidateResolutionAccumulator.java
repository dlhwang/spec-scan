package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import java.util.*;

public final class CandidateResolutionAccumulator {
    private final FactCodeGraph graph;
    private final Map<String, PredicateCandidate> predicates = new HashMap<>();
    private final Map<String, BusinessRuleCandidate> businessRules = new HashMap<>();
    private final List<CandidateDiagnostic> diagnostics = new ArrayList<>();

    public CandidateResolutionAccumulator(FactCodeGraph graph) { this.graph = Objects.requireNonNull(graph); }

    public void add(PredicateCandidate candidate) {
        if (predicates.putIfAbsent(candidate.candidateId(), candidate) != null) duplicate(candidate.candidateId());
    }
    public void add(BusinessRuleCandidate candidate) {
        if (businessRules.putIfAbsent(candidate.candidateId(), candidate) != null) duplicate(candidate.candidateId());
    }
    public void diagnostic(CandidateDiagnostic diagnostic) { diagnostics.add(Objects.requireNonNull(diagnostic)); }

    public CandidateResolutionResult snapshot() {
        List<PredicateCandidate> sortedPredicates = predicates.values().stream()
            .sorted(Comparator.comparing(PredicateCandidate::candidateId)).toList();
        List<BusinessRuleCandidate> sortedRules = businessRules.values().stream()
            .sorted(Comparator.comparing(BusinessRuleCandidate::candidateId)).toList();
        List<CandidateDiagnostic> sortedDiagnostics = diagnostics.stream()
            .sorted(Comparator.comparing(CandidateDiagnostic::code).thenComparing(d -> Objects.toString(d.nodeId(), ""))).toList();
        CandidateResolutionResult result = new CandidateResolutionResult(graph.graphId(), sortedPredicates, sortedRules, sortedDiagnostics);
        new CandidateResultIntegrityValidator().validate(result, graph);
        return result;
    }
    private void duplicate(String id) { throw new IllegalArgumentException("DUPLICATE_CANDIDATE_ID: " + id); }
}
