package io.atworks.specscan.analysis.support.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import java.util.*;

/** Activates optional packs only when catalog identity, type and graph evidence all agree. */
public final class FrameworkPackActivationService {
    public FrameworkPackDecision evaluate(String packId, FrameworkCatalogEntry entry, FrameworkEvidence evidence) {
        if (entry == null || evidence == null) return FrameworkPackDecision.unresolved(packId, "UNRESOLVED_DELEGATED_GUARD");
        if (!entry.ownerType().equals(evidence.ownerType()) || !entry.method().equals(evidence.method()))
            return FrameworkPackDecision.unresolved(packId, "FRAMEWORK_IDENTITY_MISMATCH");
        if (evidence.resolvedType() == null || evidence.resolvedType().isBlank())
            return FrameworkPackDecision.unresolved(packId, "TYPE_RESOLUTION_REQUIRED");
        if (!evidence.graphPathProven()) return FrameworkPackDecision.unresolved(packId, "UNRESOLVED_DELEGATED_GUARD");
        return FrameworkPackDecision.active(packId);
    }
}
