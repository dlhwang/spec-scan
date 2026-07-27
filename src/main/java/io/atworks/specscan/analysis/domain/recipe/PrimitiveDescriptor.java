package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;
import java.util.Map;

public record PrimitiveDescriptor(String id, String category, Map<String, BindingType> inputs,
                                  Map<String, BindingType> outputs) {
    public PrimitiveDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("primitive id is required");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("primitive category is required");
        inputs = Map.copyOf(inputs == null ? Map.of() : inputs);
        outputs = Map.copyOf(outputs == null ? Map.of() : outputs);
    }
    public List<BindingType> inputTypes() { return inputs.values().stream().toList(); }
}
