package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.support.candidate.BusinessRuleCandidateFactory;
import java.util.List;
import java.util.stream.Collectors;

public final class UnresolvedCandidateFactory {
    private final BusinessRuleCandidateFactory factory = new BusinessRuleCandidateFactory();
    public BusinessRuleCandidate create(PredicateCandidate predicate) {
        CandidateDiagnostic diagnostic = new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
            "RULE_UNRESOLVED", "No valid rule matched the predicate", predicate.conditionNodeId());
        String fingerprint = predicate.evidence().stream().map(e -> e.nodeId() + ":" + e.role()).distinct().sorted()
            .collect(Collectors.joining("|"));
        return factory.create(predicate.candidateId(), null, BusinessRuleCategory.UNKNOWN,
            predicate.extractionStatus(), SemanticStatus.UNRESOLVED, TargetResolutionStatus.UNRESOLVED,
            null, 0.0, predicate.evidence(), List.of(diagnostic), fingerprint);
    }
}
