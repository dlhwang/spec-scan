package io.atworks.specscan.analysis.domain.recipe;

import java.util.List;
import java.util.Optional;

public interface SemanticPrimitiveRegistry {
    Optional<PrimitiveDescriptor> descriptor(String primitiveId);
    List<PrimitiveDescriptor> descriptors();
    default PrimitiveDescriptor require(String primitiveId) {
        return descriptor(primitiveId).orElseThrow(() ->
            new IllegalArgumentException("UNKNOWN_PRIMITIVE: " + primitiveId));
    }
}
