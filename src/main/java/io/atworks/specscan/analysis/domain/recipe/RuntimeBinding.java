package io.atworks.specscan.analysis.domain.recipe;

public record RuntimeBinding(BindingType type, Object value) {
    public RuntimeBinding { if (type == null) throw new IllegalArgumentException("binding type is required"); }
}
