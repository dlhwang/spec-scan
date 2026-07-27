package io.atworks.specscan.analysis.domain.recipe;

import java.util.*;

public record RecipeEvaluationContext(String repositoryId, String operationKey, String predicateCandidateId,
                                      EvaluationGraphView graph, RuntimeBudget budget) {
    public RecipeEvaluationContext {
        require(repositoryId, "repositoryId"); require(operationKey, "operationKey"); require(predicateCandidateId, "predicateCandidateId");
        Objects.requireNonNull(graph, "graph"); Objects.requireNonNull(budget, "budget");
    }
    private static void require(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");}
}
