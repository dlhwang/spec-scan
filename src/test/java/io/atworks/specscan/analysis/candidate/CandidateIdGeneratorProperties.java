package io.atworks.specscan.analysis.candidate;

import io.atworks.specscan.analysis.domain.candidate.SemanticStatus;
import io.atworks.specscan.analysis.support.candidate.DeterministicCandidateIdGenerator;
import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateIdGeneratorProperties {
    private final DeterministicCandidateIdGenerator generator = new DeterministicCandidateIdGenerator();

    @Property(tries = 500)
    void sameCanonicalInputProducesSameIds(@ForAll("identities") String graph,
                                           @ForAll("identities") String node) {
        assertThat(generator.forPredicate(graph, node)).isEqualTo(generator.forPredicate(graph, node));
    }

    @Property(tries = 500)
    void differentRuleIdsProduceDifferentIds(@ForAll("identities") String predicate,
                                              @ForAll("identities") String rule) {
        assertThat(generator.forBusinessRule(predicate, rule + "A", SemanticStatus.RESOLVED))
            .isNotEqualTo(generator.forBusinessRule(predicate, rule + "B", SemanticStatus.RESOLVED));
    }

    @Property(tries = 500)
    void differentEvidenceProducesDifferentBusinessCandidateIds(@ForAll("identities") String predicate,
                                                                 @ForAll("identities") String evidence) {
        assertThat(generator.forBusinessRule(predicate, "rule", SemanticStatus.RESOLVED, evidence + "A"))
            .isNotEqualTo(generator.forBusinessRule(predicate, "rule", SemanticStatus.RESOLVED, evidence + "B"));
    }

    @Provide
    Arbitrary<String> identities() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(30);
    }
}
