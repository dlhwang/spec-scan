package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.support.candidate.EvidenceMapper;
import java.util.*;

public final class ResponseInvariantAdapter {
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();

    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output,
                                      FactCodeGraph graph, List<BusinessRuleCandidate> candidates) {
        if ("void".equalsIgnoreCase(endpoint.responseBinding().type())) return output;
        Map<String, FactNode> nodes = new HashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        List<Set<String>> normalReturnFields = normalReturnFields(graph, nodes);
        List<ExecutableCondition> assertions = new ArrayList<>(output.responseAssertions());
        assertions.addAll(factoryConstantAssertions(endpoint, graph, nodes));
        for (BusinessRuleCandidate candidate : candidates) {
            NormalizedConstraint constraint = candidate.constraint();
            if (constraint == null || !("NOT_NULL".equals(constraint.operator())
                    || "NOT_EMPTY".equals(constraint.operator()))) continue;
            for (EvidenceRef origin : candidate.evidence()) {
                FactNode source = nodes.get(origin.nodeId());
                if (source == null) continue;
                for (FactNode field : responseFields(endpoint, graph, nodes, source.id())) {
                    if (normalReturnFields.isEmpty()
                            || normalReturnFields.stream().anyMatch(fields -> !fields.contains(field.snippet()))) continue;
                    List<EvidenceRef> evidence = new ArrayList<>(candidate.evidence());
                    evidence.add(evidenceMapper.fromFact(field, EvidenceRole.DOMAIN_ORIGIN));
                    assertions.add(new ExecutableCondition("BODY", "$." + field.snippet(),
                        constraint.operator(), List.of(), field.id(), candidate.ruleId(),
                        candidate.confidence(), evidence));
                }
            }
        }
        Map<String, ExecutableCondition> unique = new LinkedHashMap<>();
        assertions.forEach(value -> unique.putIfAbsent(value.targetPath() + "|" + value.operator(), value));
        return new EndpointRuleOutput(output.endpointPath(), output.requestPreconditions(),
            List.copyOf(unique.values()), output.externalStatePrerequisites(),
            output.excludedBusinessRules(), output.diagnostics());
    }

    private List<ExecutableCondition> factoryConstantAssertions(ApiEndpoint endpoint,
        FactCodeGraph graph, Map<String, FactNode> nodes) {
        List<String> returns = graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.RETURNS
                && edge.sourceNodeId().equals(graph.apiMethodNodeId()))
            .map(FactEdge::targetNodeId).distinct().toList();
        if (returns.isEmpty()) return List.of();
        Map<String, ConstantField> common = null;
        for (String returned : returns) {
            Map<String, ConstantField> current = constantsOnPath(endpoint, graph, nodes,
                reachableFrom(graph, returned));
            if (common == null) common = new LinkedHashMap<>(current);
            else common.entrySet().removeIf(entry -> !entry.getValue().expected()
                .equals(Optional.ofNullable(current.get(entry.getKey())).map(ConstantField::expected)
                    .orElse(null)));
        }
        if (common == null) return List.of();
        return common.values().stream().map(value -> new ExecutableCondition("BODY",
            "$." + value.fieldName(), "EQ", List.of(value.expected()), value.source().id(),
            "RESPONSE_FACTORY_CONSTANT", 1.0,
            List.of(evidenceMapper.fromFact(value.source(), EvidenceRole.PREDICATE),
                evidenceMapper.fromFact(value.field(), EvidenceRole.DOMAIN_ORIGIN)))).toList();
    }

    private Map<String, ConstantField> constantsOnPath(ApiEndpoint endpoint, FactCodeGraph graph,
        Map<String, FactNode> nodes, Set<String> reachable) {
        Map<String, ConstantField> constants = new LinkedHashMap<>();
        for (FactEdge edge : graph.edges()) {
            if (edge.type() != FactEdgeType.VALUE_FLOWS_TO || !reachable.contains(edge.sourceNodeId())) continue;
            FactNode source = nodes.get(edge.sourceNodeId());
            FactNode field = nodes.get(edge.targetNodeId());
            if (source == null || field == null || !belongsToResponse(endpoint, field)) continue;
            String expected = source.payload() instanceof FactNodePayload.LiteralPayload literal
                ? literal.value() : source.payload() instanceof FactNodePayload.NullLiteralPayload ? "null" : null;
            if (expected == null) continue;
            String fieldName = field.payload() instanceof FactNodePayload.FieldAccessPayload payload
                ? payload.fieldName() : field.snippet();
            constants.putIfAbsent(fieldName, new ConstantField(fieldName, expected, source, field));
        }
        return constants;
    }

    private Set<String> reachableFrom(FactCodeGraph graph, String root) {
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reachable.add(current)) continue;
            graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(current)
                    && (edge.type() == FactEdgeType.OPERAND_OF
                    || edge.type() == FactEdgeType.CALLS
                        && Set.of("TARGET", "SOURCE_FALLBACK").contains(edge.role())
                    || edge.type() == FactEdgeType.RETURNS
                    || edge.type() == FactEdgeType.VALUE_FLOWS_TO))
                .map(FactEdge::targetNodeId).forEach(pending::addLast);
        }
        return reachable;
    }

    private record ConstantField(String fieldName, String expected, FactNode source, FactNode field) {}

    private List<Set<String>> normalReturnFields(FactCodeGraph graph, Map<String, FactNode> nodes) {
        List<String> returns = graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.RETURNS
                && edge.sourceNodeId().equals(graph.apiMethodNodeId()))
            .map(FactEdge::targetNodeId).distinct().toList();
        List<Set<String>> result = new ArrayList<>();
        for (String returned : returns) {
            Set<String> visited = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>(); pending.add(returned);
            Set<String> fields = new LinkedHashSet<>();
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                if (!visited.add(current)) continue;
                FactNode node = nodes.get(current);
                if (node != null && node.type() == FactNodeType.VALUE_FIELD) fields.add(node.snippet());
                graph.edges().stream().filter(edge -> edge.sourceNodeId().equals(current)
                        && (edge.type() == FactEdgeType.OPERAND_OF
                        || edge.type() == FactEdgeType.VALUE_FLOWS_TO))
                    .map(FactEdge::targetNodeId).forEach(pending::addLast);
            }
            result.add(fields);
        }
        return result;
    }

    private List<FactNode> responseFields(ApiEndpoint endpoint, FactCodeGraph graph,
                                          Map<String, FactNode> nodes, String originId) {
        Set<String> values = new LinkedHashSet<>(); values.add(originId);
        boolean changed;
        do {
            int size = values.size();
            graph.edges().stream().filter(edge -> values.contains(edge.sourceNodeId())
                    && (edge.type() == FactEdgeType.READS || edge.type() == FactEdgeType.ORIGINATES_FROM
                    && "CALL_ARGUMENT".equals(edge.role())))
                .map(FactEdge::targetNodeId).forEach(values::add);
            changed = values.size() != size;
        } while (changed);
        return graph.edges().stream().filter(edge -> edge.type() == FactEdgeType.VALUE_FLOWS_TO
                && values.contains(edge.sourceNodeId())).map(edge -> nodes.get(edge.targetNodeId()))
            .filter(Objects::nonNull).filter(field -> belongsToResponse(endpoint, field)).toList();
    }

    private boolean belongsToResponse(ApiEndpoint endpoint, FactNode field) {
        if (!(field.payload() instanceof FactNodePayload.FieldAccessPayload payload)) return false;
        String responseType = endpoint.responseBinding().type();
        int generic = responseType.indexOf('<');
        if (generic >= 0) responseType = responseType.substring(0, generic);
        int dot = responseType.lastIndexOf('.');
        if (dot >= 0) responseType = responseType.substring(dot + 1);
        return payload.rootExpressionKind().contains(responseType);
    }
}
