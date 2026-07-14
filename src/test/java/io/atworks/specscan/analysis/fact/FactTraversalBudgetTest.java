package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FactTraversalBudgetTest {
    @Test void accumulatorNeverExceedsEdgeBudgetAndReportsLimitAttempt() {
        FactGraphTraversalBudget budget = new FactGraphTraversalBudget(1, 1, 1);
        FactGraphAccumulator accumulator = new FactGraphAccumulator(budget);
        SourceRange range = new SourceRange("A.java", 1, 1, 1, 2);
        FactNode root = node("r", true, range); FactNode a = node("a", false, range); FactNode b = node("b", false, range);
        assertThat(accumulator.addRelation(root, a, new FactEdge("e1", "r", "a", FactEdgeType.CALLS, -1, "TARGET"))).isTrue();
        assertThat(accumulator.addRelation(root, b, new FactEdge("e2", "r", "b", FactEdgeType.CALLS, -1, "TARGET"))).isFalse();
        assertThat(accumulator.edgeCount()).isEqualTo(1);
        assertThat(accumulator.edgeLimitReached()).isTrue();
        assertThat(accumulator.nodes()).extracting(FactNode::id).containsExactly("r", "a");
    }
    private FactNode node(String id, boolean api, SourceRange range) { return new FactNode(id, api ? FactNodeType.API_METHOD : FactNodeType.METHOD, range, "m", TypeResolution.resolvedSignature("A.m()"), new FactNodePayload.MethodPayload("A", "m()", api)); }
}
