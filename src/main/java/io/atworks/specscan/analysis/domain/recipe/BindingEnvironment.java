package io.atworks.specscan.analysis.domain.recipe;

import java.util.*;

public final class BindingEnvironment {
    private final Map<String, RuntimeBinding> slots = new TreeMap<>();
    public BindingEnvironment(Map<String, RuntimeBinding> initial) { if (initial != null) initial.forEach(this::put); }
    public void put(String name, RuntimeBinding binding) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("binding name is required");
        RuntimeBinding previous = slots.get(name);
        if (previous != null && previous.type() != binding.type()) throw new IllegalArgumentException("BINDING_TYPE_REASSIGNMENT: " + name);
        slots.put(name, Objects.requireNonNull(binding, "binding"));
    }
    public Optional<RuntimeBinding> get(String name) { return Optional.ofNullable(slots.get(name)); }
    public Map<String, RuntimeBinding> snapshot() { return Collections.unmodifiableMap(new TreeMap<>(slots)); }
}
