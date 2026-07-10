package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.GraphEdge;
import io.atworks.specscan.analysis.domain.GraphNode;
import io.atworks.specscan.analysis.domain.GraphNodeType;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphContextSelector {

    private static final int DEFAULT_MAX_NODES = 8;
    private static final int DEFAULT_MAX_EDGES = 10;
    private static final int MAX_SNIPPET_LENGTH = 140;

    public GraphContext selectContext(CandidateChunk chunk, ValidationEvidenceGraph graph) {
        return selectContext(chunk, graph, DEFAULT_MAX_NODES, DEFAULT_MAX_EDGES);
    }

    public GraphContext selectContext(
        CandidateChunk chunk,
        ValidationEvidenceGraph graph,
        int maxNodes,
        int maxEdges
    ) {
        if (graph == null) {
            return new GraphContext(List.of(), List.of(), 0, 0);
        }

        Map<String, GraphNode> nodeIndex = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            nodeIndex.put(node.id(), node);
        }

        Set<String> seedNodeIds = new LinkedHashSet<>();
        for (GraphNode node : graph.nodes()) {
            if (node.type() == GraphNodeType.ENDPOINT && node.id().endsWith(":" + chunk.endpointPath())) {
                seedNodeIds.add(node.id());
            }
        }

        Set<String> candidateFields = new LinkedHashSet<>();
        for (ValidationCandidate candidate : chunk.candidates()) {
            String fieldName = normalizeTargetPath(candidate.targetPath());
            if (!fieldName.isBlank()) {
                candidateFields.add(fieldName);
            }
        }
        for (GraphNode node : graph.nodes()) {
            if (node.type() == GraphNodeType.DTO_FIELD && candidateFields.stream().anyMatch(field -> node.label().endsWith("." + field))) {
                seedNodeIds.add(node.id());
            }
        }

        Set<String> collectedNodeIds = expandRelevantNodeIds(seedNodeIds, graph.edges(), maxNodes);
        List<String> nodeSummaries = new ArrayList<>();
        for (String nodeId : collectedNodeIds) {
            GraphNode node = nodeIndex.get(nodeId);
            if (node == null) {
                continue;
            }
            nodeSummaries.add(formatNode(node));
            if (nodeSummaries.size() >= maxNodes) {
                break;
            }
        }

        List<String> edgeSummaries = new ArrayList<>();
        for (GraphEdge edge : graph.edges()) {
            if (!collectedNodeIds.contains(edge.sourceId()) && !collectedNodeIds.contains(edge.targetId())) {
                continue;
            }
            edgeSummaries.add(formatEdge(edge));
            if (edgeSummaries.size() >= maxEdges) {
                break;
            }
        }

        return new GraphContext(nodeSummaries, edgeSummaries, graph.nodes().size(), graph.edges().size());
    }

    private Set<String> expandRelevantNodeIds(Set<String> seedNodeIds, List<GraphEdge> edges, int maxNodes) {
        Set<String> visited = new LinkedHashSet<>(seedNodeIds);
        Deque<String> queue = new ArrayDeque<>(seedNodeIds);
        while (!queue.isEmpty() && visited.size() < maxNodes) {
            String current = queue.removeFirst();
            for (GraphEdge edge : edges) {
                String neighbor = null;
                if (edge.sourceId().equals(current)) {
                    neighbor = edge.targetId();
                } else if (edge.targetId().equals(current)) {
                    neighbor = edge.sourceId();
                }
                if (neighbor != null && visited.add(neighbor)) {
                    queue.addLast(neighbor);
                    if (visited.size() >= maxNodes) {
                        break;
                    }
                }
            }
        }
        return visited;
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

    private String formatNode(GraphNode node) {
        return node.type() + " | " + node.label() + " | " + trim(node.snippet());
    }

    private String formatEdge(GraphEdge edge) {
        return edge.type() + " | " + edge.sourceId() + " -> " + edge.targetId() + " | " + trim(edge.evidence());
    }

    private String trim(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH - 3) + "...";
    }

    public record GraphContext(
        List<String> nodeSummaries,
        List<String> edgeSummaries,
        int totalNodeCount,
        int totalEdgeCount
    ) {
        public boolean hasContext() {
            return !nodeSummaries.isEmpty() || !edgeSummaries.isEmpty();
        }
    }
}
