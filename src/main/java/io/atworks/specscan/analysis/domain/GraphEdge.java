package io.atworks.specscan.analysis.domain;

public record GraphEdge(
    String sourceId,
    String targetId,
    GraphEdgeType type,
    String evidence
) {}
