package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.rule.RuleExecutionStats;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import static org.assertj.core.api.Assertions.assertThat;

class RuleExecutionReportProperties {
    @Property(tries = 300, generation = GenerationMode.RANDOMIZED)
    void reportCountersReflectRecordedExecutionTrace(
            @ForAll @IntRange(min = 0, max = 10) int predicates,
            @ForAll @IntRange(min = 0, max = 10) int rules) {
        RuleExecutionStats stats = new RuleExecutionStats(rules);
        stats.predicates(predicates);
        int executions = predicates * rules;
        for (int i = 0; i < executions; i++) stats.executed();
        RuleExecutionReport report = stats.report();

        assertThat(report.registeredRules()).isEqualTo(rules);
        assertThat(report.evaluatedPredicates()).isEqualTo(predicates);
        assertThat(report.executedRules()).isEqualTo(executions);
        assertThat(report.failedRuleExecutions()).isZero();
    }
}
