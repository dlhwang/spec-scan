package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DeterministicFactNodeIdGenerator;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import static org.assertj.core.api.Assertions.assertThat;

class FactNodeIdGeneratorProperties {
    private final DeterministicFactNodeIdGenerator generator = new DeterministicFactNodeIdGenerator();

    @Property(tries = 500)
    void identicalCanonicalInputProducesSameId(@ForAll("ranges") SourceRange range, @ForAll("roles") String role) {
        String first = generator.generate(FactNodeType.CONDITION, "demo.Service.check()", range, role);
        String second = generator.generate(FactNodeType.CONDITION, "demo.Service.check()", range, role);
        assertThat(second).isEqualTo(first);
    }

    @Property(tries = 500)
    void roleChangesIdentity(@ForAll("ranges") SourceRange range, @ForAll("roles") String role) {
        assertThat(generator.generate(FactNodeType.CONDITION, "demo.Service.check()", range, role + "-other"))
            .isNotEqualTo(generator.generate(FactNodeType.CONDITION, "demo.Service.check()", range, role));
    }

    @Property(tries = 200)
    void pathSeparatorsAreNormalized(@ForAll @IntRange(min = 1, max = 500) int line) {
        SourceRange windows = new SourceRange("src\\main\\A.java", line, 1, line, 5);
        SourceRange unix = new SourceRange("src/main/A.java", line, 1, line, 5);
        assertThat(generator.generate(FactNodeType.METHOD_CALL, "A.m()", windows, "call"))
            .isEqualTo(generator.generate(FactNodeType.METHOD_CALL, "A.m()", unix, "call"));
    }

    @Provide Arbitrary<SourceRange> ranges() { return Arbitraries.integers().between(1, 500).map(line -> new SourceRange("src/main/java/demo/A.java", line, 1, line, 20)); }
    @Provide Arbitrary<String> roles() { return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(16); }
}
