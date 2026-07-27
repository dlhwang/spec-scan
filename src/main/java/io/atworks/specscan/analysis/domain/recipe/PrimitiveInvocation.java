package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;

public record PrimitiveInvocation(PrimitiveDescriptor descriptor, RecipeStep step) {
    public PrimitiveInvocation { if (descriptor == null || step == null) throw new IllegalArgumentException("invocation is required"); }
    public List<String> inputs() { return step.inputs(); }
    public String output() { return step.output(); }
}
