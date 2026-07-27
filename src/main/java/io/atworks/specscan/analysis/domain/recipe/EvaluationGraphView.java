package io.atworks.specscan.analysis.domain.recipe;

import java.util.Optional;

public interface EvaluationGraphView {
    Optional<Object> binding(String name);
    default boolean hasPredicate() { return binding("predicate").isPresent(); }
    default boolean follow(String nodeId, RuntimeBudget budget) { return budget.enter(nodeId) && budget.edge(); }
}
