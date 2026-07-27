package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import java.util.*;

public final class DefaultPrimitiveDescriptorRegistry implements SemanticPrimitiveRegistry {
    private final Map<String, PrimitiveDescriptor> descriptors;
    public DefaultPrimitiveDescriptorRegistry() {
        List<PrimitiveDescriptor> values = List.of(
            descriptor("match.failure_condition", "MATCH", Map.of(), BindingType.FAILURE),
            descriptor("match.composite_requirement", "MATCH", Map.of(), BindingType.FAILURE),
            descriptor("match.null_empty_guard", "MATCH", Map.of(), BindingType.FAILURE),
            descriptor("match.optional_lookup_failure", "MATCH", Map.of(), BindingType.FAILURE),
            descriptor("follow.call_graph", "FOLLOW", Map.of("graph", BindingType.UNKNOWN), BindingType.NODE_SET),
            descriptor("match.binary_comparison", "MATCH", Map.of("failure", BindingType.FAILURE), BindingType.COMPARISON),
            descriptor("resolve.request_input_path", "RESOLVE", Map.of("comparison", BindingType.COMPARISON), BindingType.TARGET),
            descriptor("resolve.requirement_target", "RESOLVE", Map.of("failure", BindingType.FAILURE), BindingType.TARGET),
            descriptor("bind.literal_operand", "BIND", Map.of("comparison", BindingType.COMPARISON), BindingType.LITERAL),
            descriptor("bind.guard_literal", "BIND", Map.of("failure", BindingType.FAILURE), BindingType.LITERAL),
            descriptor("resolve.lookup_target", "RESOLVE", Map.of("failure", BindingType.FAILURE), BindingType.TARGET),
            descriptor("normalize.comparison_by_failure_polarity", "NORMALIZE", Map.of("comparison", BindingType.COMPARISON, "failure", BindingType.FAILURE), BindingType.OPERATOR),
            descriptor("normalize.guard_operator", "NORMALIZE", Map.of("failure", BindingType.FAILURE), BindingType.OPERATOR),
            descriptor("emit.normalized_constraint", "EMIT", Map.of("target", BindingType.TARGET, "operator", BindingType.OPERATOR, "expected", BindingType.LITERAL), BindingType.CONSTRAINT));
        Map<String, PrimitiveDescriptor> map = new TreeMap<>(); values.forEach(value -> map.put(value.id(), value)); descriptors = Map.copyOf(map);
    }
    private PrimitiveDescriptor descriptor(String id, String category, Map<String, BindingType> inputs, BindingType output) { return new PrimitiveDescriptor(id, category, inputs, Map.of("result", output)); }
    public Optional<PrimitiveDescriptor> descriptor(String primitiveId) { return Optional.ofNullable(descriptors.get(primitiveId)); }
    public List<PrimitiveDescriptor> descriptors() { return descriptors.values().stream().toList(); }
}
