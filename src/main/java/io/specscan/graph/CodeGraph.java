package io.specscan.graph;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * In-memory code graph. Nodes are stable string ids (qualified type names / method
 * signatures); typed edges connect them. Backed by JGraphT for traversal.
 */
public class CodeGraph {

    public enum NodeKind { CLASS, INTERFACE, ENUM, RECORD, METHOD, CONSTRUCTOR, FIELD, ENDPOINT }

    public enum EdgeKind { DECLARES, EXTENDS, IMPLEMENTS, CALLS, CREATES, RETURNS, PARAM_TYPE, HANDLED_BY }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeInfo(String id, NodeKind kind, String label, String file, Integer line) {
    }

    public record EdgeInfo(String from, String to, EdgeKind kind) {
    }

    public static class Edge {
        public final EdgeKind kind;

        public Edge(EdgeKind kind) {
            this.kind = kind;
        }
    }

    public final Graph<String, Edge> graph = new DirectedMultigraph<>(null, null, false);
    public final Map<String, NodeInfo> nodes = new LinkedHashMap<>();

    public void addNode(String id, NodeKind kind, String label, String file, Integer line) {
        if (nodes.containsKey(id)) return;
        nodes.put(id, new NodeInfo(id, kind, label, file, line));
        graph.addVertex(id);
    }

    public void addEdge(String from, String to, EdgeKind kind) {
        if (!nodes.containsKey(from) || !nodes.containsKey(to)) return;
        for (Edge e : graph.getAllEdges(from, to)) {
            if (e.kind == kind) return;
        }
        graph.addEdge(from, to, new Edge(kind));
    }

    /** Outgoing neighbor ids following edges of one kind. */
    public List<String> out(String from, EdgeKind kind) {
        List<String> result = new ArrayList<>();
        if (!graph.containsVertex(from)) return result;
        for (Edge e : graph.outgoingEdgesOf(from)) {
            if (e.kind == kind) result.add(graph.getEdgeTarget(e));
        }
        return result;
    }

    /** Serializable view. */
    public Map<String, Object> export() {
        List<EdgeInfo> edges = new ArrayList<>();
        for (Edge e : graph.edgeSet()) {
            edges.add(new EdgeInfo(graph.getEdgeSource(e), graph.getEdgeTarget(e), e.kind));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", new ArrayList<>(nodes.values()));
        out.put("edges", edges);
        return out;
    }
}
