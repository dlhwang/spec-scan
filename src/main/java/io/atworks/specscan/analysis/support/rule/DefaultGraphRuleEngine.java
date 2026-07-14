package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.CandidateResolutionAccumulator;
import java.util.*;

public final class DefaultGraphRuleEngine implements GraphRuleEngine {
    private final ValidationCandidateDetector detector;
    private final RulePackRegistry registry;
    public DefaultGraphRuleEngine(ValidationCandidateDetector detector, List<RulePack> packs) {
        this.detector = Objects.requireNonNull(detector);
        this.registry = new RulePackRegistry(packs);
    }

    @Override
    public GraphRuleEngineResult evaluate(FactCodeGraph graph, MethodScope scope) {
        RuleExecutionStats stats = new RuleExecutionStats(registry.rules().size());
        CandidateDetectionResult detection = detector instanceof ReportedValidationCandidateDetector reported
            ? reported.detectReported(graph, scope) : new CandidateDetectionResult(detector.detect(graph, scope), List.of());
        detection.diagnostics().forEach(stats::diagnostic);
        List<PredicateCandidate> predicates = detection.candidates().stream()
            .sorted(Comparator.comparing(PredicateCandidate::candidateId)).toList();
        stats.predicates(predicates.size());
        RuleInvocationBoundary invocation = new RuleInvocationBoundary();
        RuleMatchAccumulator matches = new RuleMatchAccumulator(stats);
        for (PredicateCandidate predicate : predicates) for (GraphRule rule : registry.rules()) {
            invocation.invoke(rule, graph, predicate, stats).forEach(matches::add);
        }

        List<BusinessRuleCandidate> finalRules = new ArrayList<>(new RuleConflictAnnotator().annotate(matches.candidates(), registry));
        Set<String> matchedPredicates = new HashSet<>();
        finalRules.forEach(candidate -> matchedPredicates.add(candidate.predicateCandidateId()));
        UnresolvedCandidateFactory unresolved = new UnresolvedCandidateFactory();
        for (PredicateCandidate predicate : predicates) if (!matchedPredicates.contains(predicate.candidateId())) {
            finalRules.add(unresolved.create(predicate));
        }

        CandidateResolutionAccumulator accumulator = new CandidateResolutionAccumulator(graph);
        predicates.forEach(accumulator::add);
        finalRules.forEach(accumulator::add);
        return new GraphRuleEngineResult(accumulator.snapshot(), stats.report());
    }
}
