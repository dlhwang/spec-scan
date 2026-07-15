package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.fact.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FactDomainModelTest {
    private final SourceRange range = new SourceRange("src/main/java/demo/A.java", 1, 1, 1, 10);

    @Test void graphIsImmutableAndRejectsDanglingEdges() {
        FactNode root = method("root", true); FactNode child = method("child", false);
        List<FactNode> nodes = new ArrayList<>(List.of(root, child));
        FactEdge edge = new FactEdge("e", root.id(), child.id(), FactEdgeType.CALLS, -1, "TARGET");
        FactCodeGraph graph = new FactCodeGraph("g", root.id(), nodes, List.of(edge));
        nodes.clear();
        assertThat(graph.nodes()).hasSize(2);
        assertThatThrownBy(() -> graph.nodes().add(root)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new FactCodeGraph("bad", root.id(), List.of(root), List.of(edge))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dangling");
    }

    @Test void payloadAndBudgetInvariantsAreEnforced() {
        assertThatThrownBy(() -> new FactNode("x", FactNodeType.CONDITION, range, "x", TypeResolution.notApplicable(), new FactNodePayload.MethodPayload("A", "m()", false))).isInstanceOf(IllegalArgumentException.class);
        assertThat(FactGraphTraversalBudget.defaults()).isEqualTo(new FactGraphTraversalBudget(8, 100, 2000));
        assertThatThrownBy(() -> new FactGraphTraversalBudget(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private FactNode method(String id, boolean api) { return new FactNode(id, api ? FactNodeType.API_METHOD : FactNodeType.METHOD, range, "void m()", TypeResolution.resolvedSignature("demo.A.m()"), new FactNodePayload.MethodPayload("demo.A", "m()", api)); }
}
