package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.candidate.DeterministicCandidateIdGenerator;
import io.atworks.specscan.analysis.support.candidate.EvidenceMapper;
import io.atworks.specscan.analysis.support.candidate.PredicateTypeClassifier;
import java.util.*;

final class DelegatedBooleanPredicatePromoter {
    private static final int MAX_CALL_DEPTH = 2;
    private final DeterministicCandidateIdGenerator ids = new DeterministicCandidateIdGenerator();
    private final PredicateTypeClassifier classifier = new PredicateTypeClassifier();
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();

    List<PredicateCandidate> promote(FactCodeGraph graph, List<PredicateCandidate> directCandidates) {
        Map<String, FactNode> nodes = new HashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        List<PredicateCandidate> result = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (PredicateCandidate direct : directCandidates) {
            FactNode outerCondition = nodes.get(direct.conditionNodeId());
            List<PredicateCandidate> promoted = outerCondition == null ? List.of()
                : promoteCondition(graph, nodes, outerCondition, direct);
            if (promoted.isEmpty()) {
                if (emitted.add(direct.conditionNodeId())) result.add(direct);
            } else {
                for (PredicateCandidate candidate : promoted) {
                    if (emitted.add(candidate.conditionNodeId())) result.add(candidate);
                }
            }
        }
        return result;
    }

    private List<PredicateCandidate> promoteCondition(FactCodeGraph graph, Map<String, FactNode> nodes,
                                                       FactNode outerCondition, PredicateCandidate direct) {
        boolean requiredReturn = failureRequiresTrue(outerCondition);
        return promoteCondition(graph, nodes, outerCondition, direct, requiredReturn, 1,
            new LinkedHashSet<>());
    }

    private List<PredicateCandidate> promoteCondition(FactCodeGraph graph, Map<String, FactNode> nodes,
                                                       FactNode outerCondition, PredicateCandidate direct,
                                                       boolean requiredReturn, int depth,
                                                       Set<String> callPath) {
        List<PredicateCandidate> result = new ArrayList<>();
        for (FactNode call : operandDescendants(graph, nodes, outerCondition.id())) {
            if (call.type() != FactNodeType.METHOD_CALL) continue;
            for (FactNode target : callTargets(graph, nodes, call.id())) {
                if (callPath.contains(target.id())) {
                    result.add(partial(direct, outerCondition, "DELEGATED_CALL_CYCLE",
                        "Delegated boolean call cycle was stopped", target.id()));
                    continue;
                }
                if (depth > MAX_CALL_DEPTH) {
                    result.add(partial(direct, outerCondition, "DELEGATED_MAX_DEPTH_EXCEEDED",
                        "Delegated boolean propagation exceeded max depth " + MAX_CALL_DEPTH, target.id()));
                    continue;
                }
                Set<String> nextPath = new LinkedHashSet<>(callPath);
                nextPath.add(target.id());
                boolean argumentsMapped = argumentsMapped(graph, call, target);
                for (FactNode innerCondition : controlledConditions(graph, nodes, target.id())) {
                    if (!returnsOnControlledPath(graph, nodes, innerCondition.id(), requiredReturn)) continue;
                    List<PredicateCandidate> nested = promoteCondition(graph, nodes, innerCondition, direct,
                        requiredReturn, depth + 1, nextPath);
                    if (!nested.isEmpty()) {
                        result.addAll(nested);
                        continue;
                    }
                    List<EvidenceRef> evidence = new ArrayList<>();
                    evidence.add(evidenceMapper.fromFact(innerCondition, EvidenceRole.PREDICATE));
                    evidence.add(evidenceMapper.fromFact(call, EvidenceRole.CALL));
                    direct.evidence().stream().filter(item -> item.role() == EvidenceRole.FAILURE_OUTCOME)
                        .forEach(evidence::add);
                    List<CandidateDiagnostic> diagnostics = new ArrayList<>(direct.diagnostics());
                    ExtractionStatus status = direct.extractionStatus();
                    if (!argumentsMapped) {
                        status = ExtractionStatus.PARTIAL;
                        diagnostics.add(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
                            "DELEGATED_ARGUMENT_MAPPING_PARTIAL",
                            "Some delegated call arguments could not be mapped to target parameters", call.id()));
                    }
                    result.add(new PredicateCandidate(ids.forPredicate(graph.graphId(), innerCondition.id()),
                        graph.graphId(), innerCondition.id(), classifier.classify(graph, innerCondition),
                        status, evidence, diagnostics));
                }
            }
        }
        return result;
    }

    private boolean argumentsMapped(FactCodeGraph graph, FactNode call, FactNode target) {
        long arguments = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(call.id())
            && edge.type() == FactEdgeType.OPERAND_OF && "ARGUMENT".equals(edge.role())).count();
        long parameters = graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(target.id())
            && edge.type() == FactEdgeType.ORIGINATES_FROM && "PARAMETER".equals(edge.role())).count();
        long mapped = graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.VALUE_FLOWS_TO
            && "CALL_ARGUMENT_TO_PARAMETER".equals(edge.role())).filter(edge -> graph.edges().stream()
                .anyMatch(origin -> origin.sourceNodeId().equals(target.id())
                    && origin.targetNodeId().equals(edge.targetNodeId()))).count();
        return arguments == parameters && mapped >= parameters;
    }

    private PredicateCandidate partial(PredicateCandidate source, FactNode condition, String code,
                                       String message, String nodeId) {
        List<CandidateDiagnostic> diagnostics = new ArrayList<>(source.diagnostics());
        diagnostics.add(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING, code, message, nodeId));
        return new PredicateCandidate(source.candidateId(), source.graphId(), condition.id(),
            source.predicateType(), ExtractionStatus.PARTIAL, source.evidence(), diagnostics);
    }

    private boolean failureRequiresTrue(FactNode condition) {
        if (!(condition.payload() instanceof FactNodePayload.ConditionPayload payload)) return true;
        return !"!".equals(payload.rootOperator());
    }

    private boolean returnsOnControlledPath(FactCodeGraph graph, Map<String, FactNode> nodes,
                                            String conditionId, boolean requiredReturn) {
        for (FactEdge edge : graph.edges()) {
            if (!edge.sourceNodeId().equals(conditionId) || edge.type() != FactEdgeType.THEN_OUTCOME) continue;
            FactNode outcome = nodes.get(edge.targetNodeId());
            if (outcome == null || outcome.type() != FactNodeType.RETURN) continue;
            Boolean literal = returnedBooleanLiteral(graph, nodes, outcome.id());
            if (Boolean.valueOf(requiredReturn).equals(literal)
                    || requiredReturn && literal == null && hasBooleanExpression(graph, nodes, outcome.id())) {
                return true;
            }
        }
        return false;
    }

    private Boolean returnedBooleanLiteral(FactCodeGraph graph, Map<String, FactNode> nodes, String returnId) {
        for (FactEdge edge : graph.edges()) {
            if (!edge.sourceNodeId().equals(returnId) || edge.type() != FactEdgeType.OPERAND_OF) continue;
            FactNode value = nodes.get(edge.targetNodeId());
            if (value != null && value.payload() instanceof FactNodePayload.LiteralPayload literal
                    && "BooleanLiteralExpr".equals(literal.literalKind())) {
                return Boolean.valueOf(literal.value());
            }
        }
        return null;
    }

    private boolean hasBooleanExpression(FactCodeGraph graph, Map<String, FactNode> nodes, String returnId) {
        return graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(returnId)
                && edge.type() == FactEdgeType.OPERAND_OF)
            .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull)
            .anyMatch(node -> node.type() == FactNodeType.CONDITION);
    }

    private List<FactNode> operandDescendants(FactCodeGraph graph, Map<String, FactNode> nodes, String root) {
        List<FactNode> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (FactEdge edge : graph.edges()) {
                if (!edge.sourceNodeId().equals(current) || edge.type() != FactEdgeType.OPERAND_OF) continue;
                FactNode node = nodes.get(edge.targetNodeId());
                if (node != null) {
                    result.add(node);
                    pending.addLast(node.id());
                }
            }
        }
        return result;
    }

    private List<FactNode> callTargets(FactCodeGraph graph, Map<String, FactNode> nodes, String callId) {
        return graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(callId)
                && edge.type() == FactEdgeType.CALLS && "TARGET".equals(edge.role()))
            .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull)
            .filter(node -> node.type() == FactNodeType.METHOD).toList();
    }

    private List<FactNode> controlledConditions(FactCodeGraph graph, Map<String, FactNode> nodes, String methodId) {
        return graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(methodId)
                && edge.type() == FactEdgeType.CONTROLS)
            .map(edge -> nodes.get(edge.targetNodeId())).filter(Objects::nonNull)
            .filter(node -> node.type() == FactNodeType.CONDITION).toList();
    }
}
