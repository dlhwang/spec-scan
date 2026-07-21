package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.candidate.EvidenceMapper;
import io.atworks.specscan.ingestion.domain.SourceTrace;

public final class ResponseMetadataAdapter {
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();

    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output) {
        return augment(endpoint, output, null);
    }

    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output, FactCodeGraph graph) {
        List<CandidateOutputDiagnostic> diagnostics = new ArrayList<>(output.diagnostics());
        List<ExecutableCondition> assertions = new ArrayList<>(output.responseAssertions());
        if (endpoint.responseBinding().explicitStatus() != null) {
            assertions.add(statusAssertion(endpoint, graph));
            if (endpoint.responseBinding().statusSource().contains("ResponseEntity.noContent"))
                assertions.add(emptyBodyAssertion(endpoint, graph));
        } else if (endpoint.responseBinding().statusSource() != null
                && endpoint.responseBinding().statusSource().startsWith("CONFLICT:")) {
            diagnostics.add(new CandidateOutputDiagnostic("RESPONSE_METADATA_CONFLICT",
                endpoint.responseBinding().statusSource(), "response:" + endpoint.httpMethod() + ":" + endpoint.path(), null));
        } else if (assertions.isEmpty() && isSpringHandler(graph)) {
            assertions.add(frameworkDefaultStatusAssertion(graph));
        } else if (assertions.isEmpty() && endpoint.responseBinding().explicitHeaders().isEmpty()) {
            diagnostics.add(new CandidateOutputDiagnostic("RESPONSE_METADATA_UNRESOLVED",
                "No explicit normal response status, body condition, or header assertion was observed",
                "response:" + endpoint.httpMethod() + ":" + endpoint.path(), null));
        }
        endpoint.responseBinding().explicitHeaders().forEach((name, value) ->
            assertions.add(headerAssertion(endpoint, name, value)));
        return new EndpointRuleOutput(output.endpointPath(), output.requestPreconditions(),
            assertions, output.externalStatePrerequisites(), output.excludedBusinessRules(), diagnostics);
    }

    private boolean isSpringHandler(FactCodeGraph graph) {
        if (graph == null) return false;
        Set<String> mappings = Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping",
            "PatchMapping", "DeleteMapping");
        Set<String> annotations = new HashSet<>();
        graph.edges().stream()
            .filter(edge -> edge.sourceNodeId().equals(graph.apiMethodNodeId()))
            .filter(edge -> edge.type() == FactEdgeType.HAS_ANNOTATION)
            .map(FactEdge::targetNodeId)
            .forEach(annotations::add);
        return graph.nodes().stream().filter(node -> annotations.contains(node.id()))
            .filter(node -> node.payload() instanceof FactNodePayload.AnnotationPayload)
            .map(node -> ((FactNodePayload.AnnotationPayload) node.payload()).annotationType())
            .anyMatch(mappings::contains);
    }

    private ExecutableCondition frameworkDefaultStatusAssertion(FactCodeGraph graph) {
        FactNode mapping = graph.edges().stream()
            .filter(edge -> edge.sourceNodeId().equals(graph.apiMethodNodeId()))
            .filter(edge -> edge.type() == FactEdgeType.HAS_ANNOTATION)
            .map(edge -> graph.nodes().stream()
                .filter(node -> node.id().equals(edge.targetNodeId())).findFirst().orElse(null))
            .filter(Objects::nonNull)
            .filter(node -> node.payload() instanceof FactNodePayload.AnnotationPayload annotation
                && annotation.annotationType().endsWith("Mapping"))
            .findFirst().orElseThrow();
        EvidenceRef evidence = evidenceMapper.fromFact(mapping, EvidenceRole.DOMAIN_ORIGIN);
        return new ExecutableCondition("STATUS", "$status", "EQ", List.of("200"),
            "Spring MVC default success status", "SPRING_MVC_DEFAULT_RESPONSE_STATUS", 1.0,
            List.of(evidence));
    }

    private ExecutableCondition statusAssertion(ApiEndpoint endpoint, FactCodeGraph graph) {
        SourceTrace trace = endpoint.responseBinding().sourceTrace();
        int start = trace == null ? 1 : Math.max(1, trace.startLine());
        int end = trace == null ? start : Math.max(start, trace.endLine());
        String file = trace == null || trace.fileRelativePath() == null || trace.fileRelativePath().isBlank()
            ? "unknown" : trace.fileRelativePath();
        EvidenceRef evidence = graphEvidence(graph, endpoint.responseBinding().statusSource())
            .orElseGet(() -> new EvidenceRef("response-status:" + start, file, start, 1, end, 1,
                EvidenceRole.INPUT_ORIGIN, endpoint.responseBinding().statusSource()));
        return new ExecutableCondition("STATUS", "$status", "EQ",
            List.of(String.valueOf(endpoint.responseBinding().explicitStatus())),
            endpoint.responseBinding().statusSource(), "RESPONSE_STATUS_METADATA", 1.0, List.of(evidence));
    }

    private ExecutableCondition emptyBodyAssertion(ApiEndpoint endpoint, FactCodeGraph graph) {
        SourceTrace trace = endpoint.responseBinding().sourceTrace();
        int line = trace == null ? 1 : Math.max(1, trace.startLine());
        String file = trace == null ? "unknown" : trace.fileRelativePath();
        EvidenceRef evidence = graphEvidence(graph, "noContent").orElseGet(() -> new EvidenceRef(
            "response-body:" + line, file, line, 1, line, 1, EvidenceRole.DOMAIN_ORIGIN,
            "ResponseEntity.noContent"));
        return new ExecutableCondition("BODY", "$body", "EMPTY", List.of(),
            "ResponseEntity.noContent", "RESPONSE_BODY_METADATA", 1.0, List.of(evidence));
    }

    private Optional<EvidenceRef> graphEvidence(FactCodeGraph graph, String source) {
        if (graph == null || source == null) return Optional.empty();
        String token = source.substring(source.lastIndexOf('.') + 1);
        return graph.nodes().stream().filter(node -> node.type() == FactNodeType.METHOD_CALL
                || node.type() == FactNodeType.OBJECT_CREATION || node.type() == FactNodeType.API_METHOD)
            .filter(node -> node.payload() instanceof FactNodePayload.MethodCallPayload call
                    ? call.methodName().equals(token) : node.snippet().contains(source))
            .sorted(Comparator.comparing(FactNode::id)).findFirst()
            .map(node -> evidenceMapper.fromFact(node, EvidenceRole.DOMAIN_ORIGIN));
    }

    private ExecutableCondition headerAssertion(ApiEndpoint endpoint, String name, String value) {
        SourceTrace trace = endpoint.responseBinding().sourceTrace();
        int start = trace == null ? 1 : Math.max(1, trace.startLine());
        int end = trace == null ? start : Math.max(start, trace.endLine());
        String file = trace == null || trace.fileRelativePath() == null || trace.fileRelativePath().isBlank()
            ? "unknown" : trace.fileRelativePath();
        EvidenceRef evidence = new EvidenceRef("response-header:" + name + ":" + start, file, start, 1, end, 1,
            EvidenceRole.INPUT_ORIGIN, "ResponseEntity.header");
        return new ExecutableCondition("RESPONSE_HEADER", "$." + name, "EQ", List.of(value),
            "ResponseEntity.header", "RESPONSE_HEADER_METADATA", 1.0, List.of(evidence));
    }
}
