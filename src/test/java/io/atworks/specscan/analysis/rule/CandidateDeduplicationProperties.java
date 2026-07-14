package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.support.rule.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateDeduplicationProperties {
    @Property(tries = 300, generation = GenerationMode.RANDOMIZED)
    void duplicateMultiplicityDoesNotChangeUniqueResult(@ForAll @IntRange(min = 1, max = 30) int copies) {
        RuleExecutionStats stats = new RuleExecutionStats(1);
        RuleMatchAccumulator accumulator = new RuleMatchAccumulator(stats);
        BusinessRuleCandidate candidate = RuleTestFixtures.ruleCandidate(
            "same", BusinessRuleCategory.STATE_PRECONDITION, "condition");
        for (int i = 0; i < copies; i++) accumulator.add(candidate);

        assertThat(accumulator.candidates()).containsExactly(candidate);
        assertThat(stats.report().matchedCandidates()).isEqualTo(1);
        assertThat(stats.report().deduplicatedCandidates()).isEqualTo(copies - 1);
    }
}
