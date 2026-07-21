package io.atworks.apiintelligence.domain.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeGraphTest {

    @Test
    void sortsAndValidatesEdges() {
        CodeNode b = new CodeNode("b", CodeNodeKind.METHOD, "b", null, Map.of());
        CodeNode a = new CodeNode("a", CodeNodeKind.API, "a", null, Map.of());
        CodeEdge e = new CodeEdge("e", CodeEdgeKind.CALLS, "a", "b", null);
        assertThat(new CodeGraph("api", List.of(b, a), List.of(e), List.of()).nodes()).extracting(
            CodeNode::id).containsExactly("a", "b");
        assertThatThrownBy(
            () -> new CodeGraph("api", List.of(a), List.of(e), List.of())).hasMessageContaining(
            "dangling");
    }

    @Test
    void rejectsDuplicates() {
        CodeNode a = new CodeNode("a", CodeNodeKind.API, "a", null, Map.of());
        assertThatThrownBy(
            () -> new CodeGraph("api", List.of(a, a), List.of(), List.of())).hasMessageContaining(
            "duplicate");
    }
}
