package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import java.util.*;

public final class DefaultSemanticPrimitiveRegistry implements SemanticPrimitiveRegistry {
    private final Map<String, SemanticPrimitive> primitives;
    public DefaultSemanticPrimitiveRegistry() {
        List<SemanticPrimitive> list = List.of(
            primitive("match.failure_condition", Map.of(), BindingType.FAILURE),
            primitive("match.composite_requirement", Map.of(), BindingType.FAILURE),
            primitive("match.null_empty_guard", Map.of(), BindingType.FAILURE),
            primitive("match.optional_lookup_failure", Map.of(), BindingType.FAILURE),
            primitive("follow.call_graph", "FOLLOW", Map.of("graph", BindingType.UNKNOWN), BindingType.NODE_SET),
            primitive("match.binary_comparison", Map.of("failure", BindingType.FAILURE), BindingType.COMPARISON),
            primitive("resolve.request_input_path", Map.of("comparison", BindingType.COMPARISON), BindingType.TARGET),
            primitive("resolve.requirement_target", Map.of("failure", BindingType.FAILURE), BindingType.TARGET),
            primitive("bind.literal_operand", Map.of("comparison", BindingType.COMPARISON), BindingType.LITERAL),
            primitive("bind.guard_literal", Map.of("failure", BindingType.FAILURE), BindingType.LITERAL),
            primitive("resolve.lookup_target", Map.of("failure", BindingType.FAILURE), BindingType.TARGET),
            primitive("normalize.comparison_by_failure_polarity", Map.of("comparison", BindingType.COMPARISON, "failure", BindingType.FAILURE), BindingType.OPERATOR),
            primitive("normalize.guard_operator", Map.of("failure", BindingType.FAILURE), BindingType.OPERATOR),
            primitive("emit.normalized_constraint", Map.of("target", BindingType.TARGET, "operator", BindingType.OPERATOR, "expected", BindingType.LITERAL), BindingType.CONSTRAINT));
        Map<String, SemanticPrimitive> map = new TreeMap<>(); list.forEach(p -> map.put(p.descriptor().id(), p)); primitives=Map.copyOf(map);
    }
    private SemanticPrimitive primitive(String id, Map<String, BindingType> inputs, BindingType output) { return primitive(id, id.substring(0, id.indexOf('.')).toUpperCase(Locale.ROOT), inputs, output); }
    private SemanticPrimitive primitive(String id, String category, Map<String, BindingType> inputs, BindingType output) {
        PrimitiveDescriptor descriptor = new PrimitiveDescriptor(id, category, inputs, Map.of("result", output));
        return new SemanticPrimitive() {
            public PrimitiveDescriptor descriptor() { return descriptor; }
            public PrimitiveOutcome execute(RecipeEvaluationContext context, BindingEnvironment bindings, PrimitiveInvocation invocation) {
                for (String input : invocation.inputs()) if (bindings.get(input).isEmpty()) return PrimitiveOutcome.unresolved("BINDING_NOT_DEFINED", input);
                if (id.startsWith("match.")) return new PrimitiveOutcome(RecipeExecutionStatus.RESOLVED, new RuntimeBinding(output, context.predicateCandidateId()), List.of());
                if (id.startsWith("follow.")) { if (!context.graph().follow(context.predicateCandidateId(), context.budget())) return PrimitiveOutcome.partial("TRAVERSAL_BUDGET_EXCEEDED", "cycle or finite evaluation budget reached"); return new PrimitiveOutcome(RecipeExecutionStatus.RESOLVED, new RuntimeBinding(output, context.predicateCandidateId()), List.of()); }
                if (id.startsWith("emit.")) {
                    Object target=bindings.get("target").map(RuntimeBinding::value).orElse(null), operator=bindings.get("operator").map(RuntimeBinding::value).orElse(null), expected=bindings.get("expected").map(RuntimeBinding::value).orElse(null);
                    if (target == null || operator == null || expected == null) return PrimitiveOutcome.unresolved("RUNTIME_BINDING_UNRESOLVED", "target/operator/value not proven");
                    return new PrimitiveOutcome(RecipeExecutionStatus.RESOLVED, new RuntimeBinding(output, Map.of("target",target,"operator",operator,"expected",expected)), List.of());
                }
                String source = invocation.inputs().isEmpty() ? null : invocation.inputs().get(0);
                Object value = source == null ? null : id.startsWith("resolve.")
                    ? context.graph().binding(source).orElse(null)
                    : context.graph().binding(source).orElseGet(() -> bindings.get(source).map(RuntimeBinding::value).orElse(null));
                if (value == null) return PrimitiveOutcome.unresolved("GRAPH_BINDING_UNRESOLVED", id);
                return new PrimitiveOutcome(RecipeExecutionStatus.RESOLVED, new RuntimeBinding(output, value), List.of());
            }
        };
    }
    public Optional<PrimitiveDescriptor> descriptor(String primitiveId) { return Optional.ofNullable(primitives.get(primitiveId)).map(SemanticPrimitive::descriptor); }
    public List<PrimitiveDescriptor> descriptors() { return primitives.values().stream().map(SemanticPrimitive::descriptor).toList(); }
    public SemanticPrimitive requireRuntime(String id) { return Optional.ofNullable(primitives.get(id)).orElseThrow(() -> new IllegalArgumentException("UNKNOWN_PRIMITIVE: " + id)); }
}
