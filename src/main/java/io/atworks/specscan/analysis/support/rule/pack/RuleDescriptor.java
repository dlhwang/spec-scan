package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.rule.RuleLayer;
import java.util.*;

public record RuleDescriptor(String ruleId, String packId, RuleLayer layer,
                             BusinessRuleCategory category, boolean initialOffering,
                             Set<EvidenceRole> requiredEvidence) {
    public RuleDescriptor {
        if (ruleId == null || ruleId.isBlank() || packId == null || packId.isBlank())
            throw new IllegalArgumentException("ruleId and packId are required");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(category, "category");
        requiredEvidence = Set.copyOf(Objects.requireNonNull(requiredEvidence, "requiredEvidence"));
        if (!requiredEvidence.contains(EvidenceRole.PREDICATE)
                || !requiredEvidence.contains(EvidenceRole.FAILURE_OUTCOME))
            throw new IllegalArgumentException("predicate and failure evidence are required");
    }
}
