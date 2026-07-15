package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import java.util.*;

public final class RuleConflictAnnotator {
    public List<BusinessRuleCandidate> annotate(List<BusinessRuleCandidate> candidates, RulePackRegistry registry) {
        Map<String, List<BusinessRuleCandidate>> grouped = new HashMap<>();
        candidates.forEach(candidate -> grouped.computeIfAbsent(candidate.predicateCandidateId(), ignored -> new ArrayList<>()).add(candidate));
        List<BusinessRuleCandidate> result = new ArrayList<>();
        for (List<BusinessRuleCandidate> group : grouped.values()) {
            boolean ambiguous = group.stream().map(BusinessRuleCandidate::category).distinct().count() > 1;
            int highestTier = group.stream().mapToInt(c -> registry.tier(c.ruleId())).max().orElse(0);
            for (BusinessRuleCandidate candidate : group) {
                List<CandidateDiagnostic> diagnostics = new ArrayList<>(candidate.diagnostics());
                if (ambiguous) diagnostics.add(diagnostic("AMBIGUOUS_RULE_MATCH", "Multiple rule categories matched", candidate));
                if (registry.tier(candidate.ruleId()) < highestTier) diagnostics.add(diagnostic(
                    "LOWER_PRECEDENCE_MATCH", "A higher precedence rule also matched", candidate));
                result.add(copy(candidate, diagnostics));
            }
        }
        return result;
    }
    private CandidateDiagnostic diagnostic(String code, String message, BusinessRuleCandidate candidate) {
        return new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING, code, message,
            candidate.evidence().get(0).nodeId());
    }
    private BusinessRuleCandidate copy(BusinessRuleCandidate c, List<CandidateDiagnostic> diagnostics) {
        return new BusinessRuleCandidate(c.candidateId(), c.predicateCandidateId(), c.ruleId(), c.category(),
            c.effect(), c.extractionStatus(), c.semanticStatus(), c.targetStatus(), c.constraint(), c.confidence(),
            c.evidence(), diagnostics);
    }
}
