package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;
import java.util.Objects;

public record RecipeStep(String op, List<String> inputs, String output) {
    public RecipeStep {
        if (op == null || op.isBlank()) throw new IllegalArgumentException("step op is required");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        if (output == null || output.isBlank()) throw new IllegalArgumentException("step output is required");
    }
}
