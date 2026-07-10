package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedConditionNormalizer {

    private static final Pattern GETTER_COMPARISON = Pattern.compile("\\.get([A-Z][A-Za-z0-9_]*)\\(\\)\\s*(==|!=|<=|>=|<|>)\\s*(\\d+|null)");
    private static final Pattern FIELD_COMPARISON = Pattern.compile("\\b([a-zA-Z_][A-Za-z0-9_]*)\\s*(==|!=|<=|>=|<|>)\\s*(\\d+|null)");
    private static final Pattern SIZE_COMPARISON = Pattern.compile("(?:\\.length\\(\\)|\\.size\\(\\))\\s*(<=|>=|<|>)\\s*(\\d+)");
    private static final Pattern THROW_PATTERN = Pattern.compile("throw\\s+new\\s+([A-Za-z0-9_$.]+)");

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
        if (!hasServiceRuleEvidence(chunk.endpointPath(), graph)) {
            return Optional.empty();
        }

        String evidence = candidate.evidenceSnippet();
        String targetPath = normalizeTargetPath(candidate.targetPath());

        if (endpoint != null && evidence.contains("matchVersion(") && hasBinding(endpoint, "version", BindingLocation.QUERY)) {
            return Optional.of(new ApiCondition(
                ConditionLocation.QUERY,
                "$.version",
                "OPTIMISTIC_LOCK_MATCH",
                "must match current resource version",
                candidate.evidenceSnippet(),
                candidate.confidence(),
                "Rule-based: service performs version comparison against request parameter",
                candidate.sourceTrace()
            ));
        }

        if (evidence.contains("hasCancellationPermission(")) {
            return Optional.of(new ApiCondition(
                ConditionLocation.AUTH,
                "$.currentUser",
                "HAS_CANCELLATION_PERMISSION",
                "orderer or ROLE_ADMIN",
                candidate.evidenceSnippet(),
                candidate.confidence(),
                "Rule-based: service enforces cancellation permission check",
                candidate.sourceTrace()
            ));
        }

        Matcher getterComparison = GETTER_COMPARISON.matcher(evidence);
        if (getterComparison.find()) {
            String field = decapitalize(getterComparison.group(1));
            String operator = getterComparison.group(2);
            String value = getterComparison.group(3);
            Optional<ApiCondition> comparisonCondition = normalizeComparison(candidate, endpoint, field, operator, value);
            if (comparisonCondition.isPresent()) {
                return comparisonCondition;
            }
        }

        Matcher fieldComparison = FIELD_COMPARISON.matcher(evidence);
        while (fieldComparison.find()) {
            String field = fieldComparison.group(1);
            if (!field.equals(targetPath)) {
                continue;
            }
            Optional<ApiCondition> comparisonCondition = normalizeComparison(candidate, endpoint, field, fieldComparison.group(2), fieldComparison.group(3));
            if (comparisonCondition.isPresent()) {
                return comparisonCondition;
            }
        }

        if (evidence.contains(".isEmpty()")) {
            return Optional.of(buildCondition(candidate, endpoint, "NOT_EMPTY", "true", "Rule-based: service rejects empty value"));
        }
        if (evidence.contains(".isBlank()")) {
            return Optional.of(buildCondition(candidate, endpoint, "NOT_BLANK", "true", "Rule-based: service rejects blank value"));
        }

        Matcher sizeComparison = SIZE_COMPARISON.matcher(evidence);
        if (sizeComparison.find()) {
            String operator = sizeComparison.group(1);
            int threshold = Integer.parseInt(sizeComparison.group(2));
            String expected = switch (operator) {
                case "<", "<=" -> "max=" + adjustMax(operator, threshold);
                case ">", ">=" -> "min=" + adjustMin(operator, threshold);
                default -> null;
            };
            if (expected != null) {
                return Optional.of(buildCondition(candidate, endpoint, "SIZE", expected, "Rule-based: service enforces size boundary"));
            }
        }

        Matcher throwMatcher = THROW_PATTERN.matcher(evidence);
        if (throwMatcher.find()) {
            String exceptionName = simplifyTypeName(throwMatcher.group(1));
            return Optional.of(buildCondition(candidate, endpoint, "BUSINESS_CONSTRAINT", exceptionName, "Rule-based: service throws " + exceptionName));
        }

        return Optional.empty();
    }

    private Optional<ApiCondition> normalizeComparison(
        ValidationCandidate candidate,
        ApiEndpoint endpoint,
        String field,
        String operator,
        String value
    ) {
        if ("null".equals(value)) {
            if ("==".equals(operator)) {
                return Optional.of(buildCondition(candidate, endpoint, "NOT_NULL", "true", "Rule-based: service rejects null value"));
            }
            return Optional.empty();
        }

        if (field.toLowerCase().contains("age") && ("<".equals(operator) || "<=".equals(operator))) {
            int minimum = adjustMinForLowerBound(operator, Integer.parseInt(value));
            return Optional.of(buildCondition(candidate, endpoint, "MIN_AGE", String.valueOf(minimum), "Rule-based: age lower bound"));
        }

        return Optional.empty();
    }

    private boolean hasServiceRuleEvidence(String endpointPath, ValidationEvidenceGraph graph) {
        if (graph == null || (graph.nodes().isEmpty() && graph.edges().isEmpty())) {
            return true;
        }

        String endpointNodeId = "ENDPOINT:POST:" + endpointPath;
        Map<String, GraphNode> nodeIndex = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            nodeIndex.put(node.id(), node);
            if (node.type() == GraphNodeType.ENDPOINT && node.id().endsWith(":" + endpointPath)) {
                endpointNodeId = node.id();
            }
        }

        for (GraphEdge edge : graph.edges()) {
            if (!edge.sourceId().equals(endpointNodeId)) {
                continue;
            }
            if (edge.type() != GraphEdgeType.CALLS) {
                continue;
            }
            GraphNode targetNode = nodeIndex.get(edge.targetId());
            if (targetNode != null && targetNode.type() == GraphNodeType.SERVICE_METHOD) {
                return true;
            }
        }

        for (GraphNode node : graph.nodes()) {
            if (node.type() == GraphNodeType.BUSINESS_RULE && node.id().contains(endpointPath)) {
                return true;
            }
        }
        return false;
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
            candidate.sourceTrace()
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

        String normalizedTarget = normalizeTargetPath(targetPath);
        return switch (resolveConditionLocation(endpoint, targetPath)) {
            case PATH, QUERY, HEADER, BODY, RESOURCE -> "$." + normalizedTarget;
            case AUTH -> "$.currentUser";
            case UNKNOWN -> "$." + normalizedTarget;
        };
    }

    private int adjustMinForLowerBound(String operator, int threshold) {
        return "<=".equals(operator) ? threshold + 1 : threshold;
    }

    private int adjustMax(String operator, int threshold) {
        return "<".equals(operator) ? threshold - 1 : threshold;
    }

    private int adjustMin(String operator, int threshold) {
        return ">".equals(operator) ? threshold + 1 : threshold;
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

    private String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private String simplifyTypeName(String typeName) {
        int index = typeName.lastIndexOf('.');
        return index == -1 ? typeName : typeName.substring(index + 1);
    }
}
