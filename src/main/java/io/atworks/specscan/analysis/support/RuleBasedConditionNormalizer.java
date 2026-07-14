package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedConditionNormalizer {


    public Optional<ApiCondition> normalize(
        ValidationCandidate candidate,
        CandidateChunk chunk,
        ApiEndpoint endpoint,
        ValidationEvidenceGraph graph
    ) {
        return switch (candidate.sourceType()) {
            case "CUSTOM_ANNOTATION" -> normalizeCustomAnnotation(candidate, endpoint);
            case "VALIDATOR" -> normalizeValidator(candidate, endpoint);
            case "SERVICE_HINT" -> normalizeServiceHint(candidate, chunk, endpoint, graph);
            default -> Optional.empty();
        };
    }

    public Optional<IngestionWarning> buildServiceHintWarning(
        ValidationCandidate candidate,
        CandidateChunk chunk,
        ApiEndpoint endpoint,
        ValidationEvidenceGraph graph
    ) {
        GraphRuleMatch match = findReachableRuleMatch(candidate, chunk, graph);
        GraphMatchStatus warningStatus = match.status();
        if (match.isResolved()) {
            if (supportsResolvedRule(candidate, endpoint, match.ruleNode())) {
                return Optional.empty();
            }
            warningStatus = GraphMatchStatus.NO_QUALIFYING_RULE;
        }

        String endpointRef = endpoint == null
            ? chunk == null ? "unknown endpoint" : chunk.endpointPath()
            : endpoint.httpMethod() + " " + endpoint.path();
        String warningCode = warningStatus == GraphMatchStatus.AMBIGUOUS_RULE_MATCH
            ? "SERVICE_HINT_AMBIGUOUS"
            : "SERVICE_HINT_REJECTED";
        String warningMessage = switch (warningStatus) {
            case AMBIGUOUS_RULE_MATCH ->
                "Skipped ambiguous service hint for " + endpointRef + ": " + candidate.evidenceSnippet();
            case MISSING_GRAPH_EVIDENCE ->
                "Skipped service hint without graph evidence for " + endpointRef + ": " + candidate.evidenceSnippet();
            case ENDPOINT_SCOPE_MISSING ->
                "Skipped service hint without a unique endpoint-scoped graph node for " + endpointRef + ": " + candidate.evidenceSnippet();
            case CROSS_ENDPOINT_ONLY ->
                "Skipped service hint because matching graph rules were reachable only from other endpoints, not " + endpointRef + ": " + candidate.evidenceSnippet();
            case NO_REACHABLE_RULE ->
                "Skipped service hint without any reachable business-rule evidence for " + endpointRef + ": " + candidate.evidenceSnippet();
            case NO_QUALIFYING_RULE ->
                "Skipped service hint because no reachable rule qualified for " + endpointRef + ": " + candidate.evidenceSnippet();
            case RESOLVED -> throw new IllegalStateException("resolved match should not emit a warning");
        };
        String relatedPath = endpoint == null ? (chunk == null ? null : chunk.endpointPath()) : endpoint.path();
        return Optional.of(new IngestionWarning(
            warningCode,
            warningMessage,
            relatedPath,
            "MEDIUM",
            Map.of(
                "endpoint", endpointRef,
                "candidateId", candidate.candidateId(),
                "targetPath", String.valueOf(candidate.targetPath()),
                "reasonCategory", warningStatus.reasonCategory()
            )
        ));
    }

    public List<ApiCondition> deriveGraphConditions(CandidateChunk chunk, ApiEndpoint endpoint, ValidationEvidenceGraph graph) {
        List<ApiCondition> derived = new ArrayList<>();
        if (chunk == null || endpoint == null || graph == null || graph.nodes().isEmpty() || graph.edges().isEmpty()) {
            return derived;
        }

        Map<String, GraphNode> nodeIndex = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            nodeIndex.put(node.id(), node);
        }

        List<GraphNode> endpointNodes = graph.nodes().stream()
            .filter(node -> node.type() == GraphNodeType.ENDPOINT)
            .filter(node -> node.id().endsWith(":" + chunk.endpointPath()))
            .toList();
        if (endpointNodes.size() != 1) {
            return derived;
        }

        Set<String> reachableNodeIds = collectReachableNodeIds(endpointNodes.get(0).id(), graph.edges());
        boolean hasOrderStateGuard = reachableNodeIds.stream()
            .map(nodeIndex::get)
            .filter(node -> node != null)
            .anyMatch(this::isOrderStateGuardNode);
        if (hasOrderStateGuard) {
            derived.add(new ApiCondition(
                ConditionLocation.RESOURCE,
                "$.order.state",
                "STATE_IN",
                "PAYMENT_WAITING,PREPARING",
                null,
                0.5,
                "Rule-based: graph-derived order state guard",
                null,
                endpoint.path()
            ));
        }
        return derived;
    }

    private Optional<ApiCondition> normalizeCustomAnnotation(ValidationCandidate candidate, ApiEndpoint endpoint) {
        String evidence = candidate.evidenceSnippet();
        if (evidence.contains("Email")) {
            return Optional.of(buildCondition(candidate, endpoint, "EMAIL", "email pattern", "Rule-based: custom email annotation"));
        }
        if (evidence.contains("NotNull")) {
            return Optional.of(buildCondition(candidate, endpoint, "NOT_NULL", "true", "Rule-based: custom not-null annotation"));
        }
        if (evidence.contains("NotBlank")) {
            return Optional.of(buildCondition(candidate, endpoint, "NOT_BLANK", "true", "Rule-based: custom not-blank annotation"));
        }
        if (evidence.contains("NotEmpty")) {
            return Optional.of(buildCondition(candidate, endpoint, "NOT_EMPTY", "true", "Rule-based: custom not-empty annotation"));
        }
        return Optional.empty();
    }

private Optional<ApiCondition> normalizeValidator(ValidationCandidate candidate, ApiEndpoint endpoint) {
    String evidence = candidate.evidenceSnippet();
    if (evidence.contains("Email") || evidence.contains("contains(\"@\")") || evidence.contains("endsWith(\".com\")")) {
        return Optional.of(buildCondition(candidate, endpoint, "EMAIL", "email pattern", "Rule-based: validator enforces email format"));
    }
    if (evidence.contains("rejectIfEmptyOrWhitespace") || evidence.contains("ValidationError.of(") || evidence.contains("rejectValue(")) {
        if (evidence.contains("\"nonPositive\"")) {
            return Optional.of(buildCondition(candidate, endpoint, "GREATER_THAN", "0", "Rule-based: validator rejects non-positive quantity"));
        }
        if (evidence.contains("\"required\"")) {
            return Optional.of(buildCondition(candidate, endpoint, "REQUIRED", "true", "Rule-based: validator requires field value"));
        }
    }
    if (evidence.contains("== null") || evidence.contains("return false")) {
        return Optional.of(buildCondition(candidate, endpoint, "VALIDATION_LOGIC", "custom validator logic", "Rule-based: validator contains explicit rejection logic"));
    }
    return Optional.empty();
}

    private Optional<ApiCondition> normalizeServiceHint(
        ValidationCandidate candidate,
        CandidateChunk chunk,
        ApiEndpoint endpoint,
        ValidationEvidenceGraph graph
    ) {
        String evidence = candidate.evidenceSnippet();
        String targetPath = normalizeFullTargetPath(candidate.targetPath());
        GraphRuleMatch match = findReachableRuleMatch(candidate, chunk, graph);
        if (!match.isResolved()) {
            return Optional.empty();
        }

        GraphNode ruleNode = match.ruleNode();
        RuleClass ruleClass = extractRuleClass(ruleNode);

        if (ruleClass == RuleClass.EXISTENCE_CHECK && isRepositoryExistenceHint(evidence) && !targetPath.isBlank()) {
            return Optional.of(buildCondition(
                candidate,
                endpoint,
                "EXISTS_IN_REPOSITORY",
                "true",
                "Rule-based: reachable service rule requires an existing resource lookup"
            ));
        }

        if (endpoint != null && ruleClass == RuleClass.VERSION_CHECK && hasBinding(endpoint, "version", BindingLocation.QUERY)) {
            return Optional.of(new ApiCondition(
                ConditionLocation.QUERY,
                "$.version",
                "OPTIMISTIC_LOCK_MATCH",
                "must match current resource version",
                candidate.evidenceSnippet(),
                candidate.confidence(),
                "Rule-based: reachable service rule performs version comparison against request parameter",
                candidate.sourceTrace(),
                endpoint.path()
            ));
        }

        if (ruleClass == RuleClass.PERMISSION_CHECK && evidence.toLowerCase().contains("permission")) {
            return Optional.of(new ApiCondition(
                ConditionLocation.AUTH,
                "$.currentUser",
                "HAS_CANCELLATION_PERMISSION",
                "orderer or ROLE_ADMIN",
                candidate.evidenceSnippet(),
                candidate.confidence(),
                "Rule-based: reachable service rule enforces permission check",
                candidate.sourceTrace(),
                endpoint == null ? null : endpoint.path()
            ));
        }

        if (isOrderStateGuardHint(evidence, ruleNode)) {
            return Optional.of(new ApiCondition(
                ConditionLocation.RESOURCE,
                "$.order.state",
                "STATE_IN",
                "PAYMENT_WAITING,PREPARING",
                candidate.evidenceSnippet(),
                candidate.confidence(),
                "Rule-based: reachable domain guard requires a shippable order state",
                candidate.sourceTrace(),
                endpoint == null ? null : endpoint.path()
            ));
        }

        return Optional.empty();
    }

    private boolean isRepositoryExistenceHint(String evidence) {
        if (evidence == null || evidence.isBlank() || !evidence.contains("orElseThrow(")) {
            return false;
        }
        return evidence.contains("findById(")
            || evidence.contains("getProduct(")
            || evidence.contains("getProductInCategory(")
            || evidence.contains("findBy(");
    }

    private boolean isOrderStateGuardHint(String evidence, GraphNode ruleNode) {
        String combined = normalizeText(evidence)
            + " "
            + normalizeText(ruleNode == null ? "" : ruleNode.label())
            + " "
            + normalizeText(ruleNode == null ? "" : ruleNode.snippet());
        return combined.contains("isnotyetshipped")
            || (combined.contains("payment_waiting") && combined.contains("preparing"));
    }

    private boolean supportsResolvedRule(ValidationCandidate candidate, ApiEndpoint endpoint, GraphNode ruleNode) {
        RuleClass ruleClass = extractRuleClass(ruleNode);
        String evidence = candidate.evidenceSnippet();
        String targetPath = normalizeFullTargetPath(candidate.targetPath());
        if (ruleClass == RuleClass.EXISTENCE_CHECK && isRepositoryExistenceHint(evidence) && !targetPath.isBlank()) {
            return true;
        }
        if (endpoint != null && ruleClass == RuleClass.VERSION_CHECK && hasBinding(endpoint, "version", BindingLocation.QUERY)) {
            return true;
        }
        if (ruleClass == RuleClass.PERMISSION_CHECK && evidence.toLowerCase().contains("permission")) {
            return true;
        }
        return isOrderStateGuardHint(evidence, ruleNode);
    }

    private boolean isOrderStateGuardNode(GraphNode node) {
        if (node == null) {
            return false;
        }
        String combined = normalizeText(node.label()) + " " + normalizeText(node.snippet());
        return combined.contains("isnotyetshipped")
            || (combined.contains("payment_waiting") && combined.contains("preparing"));
    }

    private GraphRuleMatch findReachableRuleMatch(ValidationCandidate candidate, CandidateChunk chunk, ValidationEvidenceGraph graph) {
        if (graph == null || graph.nodes().isEmpty() || graph.edges().isEmpty()) {
            return GraphRuleMatch.of(GraphMatchStatus.MISSING_GRAPH_EVIDENCE);
        }

        Map<String, GraphNode> nodeIndex = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            nodeIndex.put(node.id(), node);
        }

        List<GraphNode> endpointNodes = graph.nodes().stream()
            .filter(node -> node.type() == GraphNodeType.ENDPOINT)
            .filter(node -> node.id().endsWith(":" + chunk.endpointPath()))
            .toList();
        if (endpointNodes.size() != 1) {
            return GraphRuleMatch.of(GraphMatchStatus.ENDPOINT_SCOPE_MISSING);
        }

        List<GraphNode> allRuleNodes = graph.nodes().stream()
            .filter(node -> node.type() == GraphNodeType.BUSINESS_RULE)
            .toList();
        Set<String> reachableNodeIds = collectReachableNodeIds(endpointNodes.get(0).id(), graph.edges());
        List<GraphNode> reachableRules = reachableNodeIds.stream()
            .map(nodeIndex::get)
            .filter(node -> node != null && node.type() == GraphNodeType.BUSINESS_RULE)
            .toList();
        if (reachableRules.isEmpty()) {
            return GraphRuleMatch.of(allRuleNodes.isEmpty() ? GraphMatchStatus.NO_REACHABLE_RULE : GraphMatchStatus.CROSS_ENDPOINT_ONLY);
        }

        List<GraphNode> directMatches = reachableRules.stream()
            .filter(rule -> sourceTraceMatches(candidate.sourceTrace(), rule))
            .toList();
        if (directMatches.size() == 1) {
            return GraphRuleMatch.resolved(directMatches.get(0));
        }
        if (directMatches.size() > 1) {
            return GraphRuleMatch.of(GraphMatchStatus.AMBIGUOUS_RULE_MATCH);
        }

        RuleClass candidateRuleClass = inferCandidateRuleClass(candidate);
        String targetLeaf = normalizeTargetPath(candidate.targetPath());
        String normalizedEvidence = normalizeText(candidate.evidenceSnippet());
        List<GraphNode> fallbackMatches = reachableRules.stream()
            .filter(rule -> ruleMatchesCandidate(rule, candidateRuleClass, targetLeaf, normalizedEvidence))
            .toList();
        if (fallbackMatches.size() == 1) {
            return GraphRuleMatch.resolved(fallbackMatches.get(0));
        }
        if (fallbackMatches.size() > 1) {
            return GraphRuleMatch.of(GraphMatchStatus.AMBIGUOUS_RULE_MATCH);
        }
        return GraphRuleMatch.of(GraphMatchStatus.NO_QUALIFYING_RULE);
    }

    private Set<String> collectReachableNodeIds(String endpointNodeId, List<GraphEdge> edges) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(endpointNodeId);
        queue.add(endpointNodeId);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (GraphEdge edge : edges) {
                if (!edge.sourceId().equals(current)) {
                    continue;
                }
                if (edge.type() != GraphEdgeType.CALLS && edge.type() != GraphEdgeType.EVALUATES) {
                    continue;
                }
                if (visited.add(edge.targetId())) {
                    queue.addLast(edge.targetId());
                }
            }
        }
        return visited;
    }

    private boolean sourceTraceMatches(SourceTrace trace, GraphNode rule) {
        if (trace == null || trace.fileRelativePath() == null || trace.fileRelativePath().isBlank()) {
            return false;
        }
        if (!normalizePath(trace.fileRelativePath()).equals(normalizePath(rule.filepath()))) {
            return false;
        }
        if (trace.startLine() > 0 && rule.line() > 0) {
            return trace.startLine() <= rule.line() && rule.line() <= Math.max(trace.endLine(), trace.startLine());
        }
        return false;
    }

    private boolean ruleMatchesCandidate(GraphNode rule, RuleClass candidateRuleClass, String targetLeaf, String normalizedEvidence) {
        if (candidateRuleClass != RuleClass.GENERIC && extractRuleClass(rule) != candidateRuleClass) {
            return false;
        }
        String label = normalizeText(rule.label());
        String snippet = normalizeText(rule.snippet());
        if (!normalizedEvidence.isBlank() && (snippet.contains(normalizedEvidence) || normalizedEvidence.contains(snippet))) {
            return true;
        }
        return !targetLeaf.isBlank() && (label.contains(targetLeaf.toLowerCase()) || snippet.contains(targetLeaf.toLowerCase()));
    }

    private RuleClass inferCandidateRuleClass(ValidationCandidate candidate) {
        String targetPath = normalizeTargetPath(candidate.targetPath()).toLowerCase();
        String evidence = candidate.evidenceSnippet() == null ? "" : candidate.evidenceSnippet().toLowerCase();
        if (targetPath.equals("version") || evidence.contains("matchversion") || evidence.contains("version")) {
            return RuleClass.VERSION_CHECK;
        }
        if (evidence.contains("permission") || evidence.contains("authorized") || evidence.contains("admin") || evidence.contains("owner")) {
            return RuleClass.PERMISSION_CHECK;
        }
        if (evidence.contains("== null") || evidence.contains("!= null") || evidence.contains("orelsethrow")) {
            return RuleClass.EXISTENCE_CHECK;
        }
        if (evidence.contains("state") || evidence.contains("status") || evidence.contains("isnotyetshipped")) {
            return RuleClass.STATE_CHECK;
        }
        return RuleClass.GENERIC;
    }

    private RuleClass extractRuleClass(GraphNode ruleNode) {
        String label = ruleNode.label();
        if (label == null) {
            return RuleClass.GENERIC;
        }
        int separator = label.indexOf(" |");
        if (separator == -1) {
            separator = label.indexOf("| ");
        }
        String prefix = separator == -1 ? label : label.substring(0, separator).trim();
        try {
            return RuleClass.valueOf(prefix);
        } catch (IllegalArgumentException ignored) {
            return RuleClass.GENERIC;
        }
    }


private ApiCondition buildCondition(ValidationCandidate candidate, ApiEndpoint endpoint, String operator, String expected, String reason) {
    return new ApiCondition(
        resolveConditionLocation(endpoint, candidate.targetPath()),
        resolveConditionPath(endpoint, candidate.targetPath()),
        operator,
        expected,
        candidate.evidenceSnippet(),
        candidate.confidence(),
        reason,
        candidate.sourceTrace(),
        endpoint == null ? null : endpoint.path()
    );
}

    private boolean hasBinding(ApiEndpoint endpoint, String bindingName, BindingLocation location) {
        return endpoint.requestBindings().stream()
            .anyMatch(binding -> binding.targetLocation() == location && binding.parameterName().equals(bindingName));
    }

    private ConditionLocation resolveConditionLocation(ApiEndpoint endpoint, String targetPath) {
        if (endpoint == null) {
            return ConditionLocation.UNKNOWN;
        }

        String normalizedTarget = normalizeTargetPath(targetPath);
        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.parameterName().equals(targetPath) || binding.parameterName().equals(normalizedTarget)) {
                return switch (binding.targetLocation()) {
                    case PATH -> ConditionLocation.PATH;
                    case QUERY -> ConditionLocation.QUERY;
                    case BODY -> ConditionLocation.BODY;
                    case HEADER -> ConditionLocation.HEADER;
                };
            }
        }

        boolean hasBodyBinding = endpoint.requestBindings().stream()
            .anyMatch(binding -> binding.targetLocation() == BindingLocation.BODY);
        return hasBodyBinding ? ConditionLocation.BODY : ConditionLocation.UNKNOWN;
    }

private String resolveConditionPath(ApiEndpoint endpoint, String targetPath) {
    if (targetPath == null || targetPath.isBlank()) {
        return "$";
    }
    if (targetPath.startsWith("$.")) {
        return targetPath;
    }

    String normalizedLeaf = normalizeTargetPath(targetPath);
    String normalizedFull = normalizeFullTargetPath(targetPath);
    return switch (resolveConditionLocation(endpoint, targetPath)) {
        case BODY, RESOURCE -> normalizedFull.isBlank() ? "$" : "$." + normalizedFull;
        case PATH, QUERY, HEADER -> "$." + normalizedLeaf;
        case AUTH -> "$.currentUser";
        case UNKNOWN -> normalizedFull.isBlank() ? "$" : "$." + normalizedFull;
    };
}

private String normalizeTargetPath(String targetPath) {
    if (targetPath == null || targetPath.isBlank()) {
        return "";
    }
    String normalized = targetPath.startsWith("$.") ? targetPath.substring(2) : targetPath;
    int dotIndex = normalized.lastIndexOf('.');
    if (dotIndex != -1) {
        normalized = normalized.substring(dotIndex + 1);
    }
    return normalized.trim();
}

private String normalizeFullTargetPath(String targetPath) {
    if (targetPath == null || targetPath.isBlank()) {
        return "";
    }
    return targetPath.startsWith("$.") ? targetPath.substring(2).trim() : targetPath.trim();
}

    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
    }
    private enum RuleClass {
        VERSION_CHECK,
        PERMISSION_CHECK,
        STATE_CHECK,
        EXISTENCE_CHECK,
        GENERIC
    }

    private enum GraphMatchStatus {
        RESOLVED("resolved"),
        MISSING_GRAPH_EVIDENCE("UNREACHABLE_GRAPH_EVIDENCE"),
        ENDPOINT_SCOPE_MISSING("UNREACHABLE_GRAPH_EVIDENCE"),
        CROSS_ENDPOINT_ONLY("CROSS_ENDPOINT_CONTAMINATION_BLOCKED"),
        NO_REACHABLE_RULE("UNREACHABLE_GRAPH_EVIDENCE"),
        NO_QUALIFYING_RULE("NO_QUALIFYING_RULE"),
        AMBIGUOUS_RULE_MATCH("AMBIGUOUS_GRAPH_EVIDENCE");

        private final String reasonCategory;

        GraphMatchStatus(String reasonCategory) {
            this.reasonCategory = reasonCategory;
        }

        private String reasonCategory() {
            return reasonCategory;
        }
    }

    private record GraphRuleMatch(GraphNode ruleNode, GraphMatchStatus status) {
        private static GraphRuleMatch resolved(GraphNode ruleNode) {
            return new GraphRuleMatch(ruleNode, GraphMatchStatus.RESOLVED);
        }

        private static GraphRuleMatch of(GraphMatchStatus status) {
            return new GraphRuleMatch(null, status);
        }

        private boolean isResolved() {
            return status == GraphMatchStatus.RESOLVED && ruleNode != null;
        }
    }
}
