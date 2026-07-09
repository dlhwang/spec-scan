package io.atworks.specscan.analysis.domain;

import java.util.List;

public record ValidationEvidenceGraph(
    List<GraphNode> nodes,
    List<GraphEdge> edges
) {}
