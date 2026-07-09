package io.atworks.specscan.analysis.domain;

public record GraphNode(
    String id,
    GraphNodeType type,
    String label,
    String filepath,
    int line,
    String snippet
) {}
