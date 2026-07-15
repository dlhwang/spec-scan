package io.atworks.specscan.analysis.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeResolverTest {

    @TempDir
    Path sourceRoot;

    @Test
    void resolvesTypeWhenPatternSwitchSourceIsScannedFirst() throws Exception {
        Files.writeString(sourceRoot.resolve("PatternSwitch.java"), """
            sealed interface PatternSwitch permits Basic {}
            record Basic(String value) implements PatternSwitch {}
            final class PatternSwitchUsage {
                String value(PatternSwitch input) {
                    return switch (input) {
                        case Basic basic -> basic.value();
                    };
                }
            }
            """);
        Files.writeString(sourceRoot.resolve("Container.java"), "class Target {}");

        TypeResolver resolver = new TypeResolver(List.of(sourceRoot));

        assertThat(resolver.resolveClassDeclaration("Target")).isPresent();
    }

    @Test
    void skipsUnparseableSourceDuringSimpleNameFallback() throws Exception {
        Files.writeString(sourceRoot.resolve("Broken.java"), "class Broken { this is not Java }");
        Files.writeString(sourceRoot.resolve("Container.java"), "class Target {}");

        TypeResolver resolver = new TypeResolver(List.of(sourceRoot));

        assertThat(resolver.resolveClassDeclaration("Target")).isPresent();
    }
}
