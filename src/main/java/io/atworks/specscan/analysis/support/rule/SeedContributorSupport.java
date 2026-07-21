package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.MethodScope;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.*;

final class SeedContributorSupport {
    private final FactCodeGraph graph;
    private final Map<String, FactNode> nodes = new HashMap<>();
    private final DeterministicCandidateIdGenerator ids = new DeterministicCandidateIdGenerator();
    private final EvidenceMapper evidence = new EvidenceMapper();

    SeedContributorSupport(FactCodeGraph graph) {
        this.graph = graph;
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
    }

    FactNode node(String id) { return nodes.get(id); }

    List<FactNode> scopedCalls(MethodScope scope) {
        return graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.CALLS
                && scope.methodNodeIds().contains(edge.sourceNodeId()))
            .map(edge -> node(edge.targetNodeId())).filter(Objects::nonNull)
            .sorted(Comparator.comparing(FactNode::id)).toList();
    }

    List<FactNode> scopedConditions(MethodScope scope) {
        return graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.CONTROLS
                && scope.methodNodeIds().contains(edge.sourceNodeId()))
            .map(edge -> node(edge.targetNodeId())).filter(Objects::nonNull)
            .sorted(Comparator.comparing(FactNode::id)).toList();
    }

    List<FactNode> descendants(String sourceId) {
        List<FactNode> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(sourceId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(current)
                    && edge.type() == FactEdgeType.OPERAND_OF).forEach(edge -> {
                FactNode target = node(edge.targetNodeId());
                if (target != null) { result.add(target); pending.addLast(target.id()); }
            });
        }
        return result;
    }

    FactNode outcome(String conditionId, FactEdgeType edgeType) {
        return graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(conditionId)
                && edge.type() == edgeType)
            .map(edge -> node(edge.targetNodeId())).filter(Objects::nonNull).findFirst().orElse(null);
    }

    PredicateCandidate candidate(FactNode predicate, PredicateType type, FactNode outcome) {
        List<EvidenceRef> refs = new ArrayList<>();
        refs.add(evidence.fromFact(predicate, EvidenceRole.PREDICATE));
        refs.add(evidence.fromFact(outcome == null ? predicate : outcome, EvidenceRole.FAILURE_OUTCOME));
        return new PredicateCandidate(ids.forPredicate(graph.graphId(), predicate.id()), graph.graphId(),
            predicate.id(), type, ExtractionStatus.EXTRACTED, refs, List.of());
    }
}
