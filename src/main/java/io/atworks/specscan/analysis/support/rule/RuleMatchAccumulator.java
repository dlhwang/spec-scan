package io.atworks.specscan.analysis.support.rule;

import io.atworks.specscan.analysis.domain.candidate.BusinessRuleCandidate;
import java.util.*;

public final class RuleMatchAccumulator {
    private final CandidateMatchKeyFactory keys = new CandidateMatchKeyFactory();
    private final Map<CandidateMatchKeyFactory.CandidateMatchKey, BusinessRuleCandidate> unique = new HashMap<>();
    private final RuleExecutionStats stats;
    public RuleMatchAccumulator(RuleExecutionStats stats) { this.stats = stats; }
    public void add(BusinessRuleCandidate candidate) {
        if (unique.putIfAbsent(keys.key(candidate), candidate) == null) stats.matched(candidate.ruleId()); else stats.deduplicated();
    }
    public List<BusinessRuleCandidate> candidates() { return List.copyOf(unique.values()); }
}
