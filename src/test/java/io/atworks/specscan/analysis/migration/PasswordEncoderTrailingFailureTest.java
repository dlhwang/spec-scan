package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTrailingFailureTest {
    @TempDir Path workspace;

    @Test void followsDelegatedBooleanHelperToPasswordEncoderMatch() throws Exception {
        write("sample/controller/AuthController.java", """
            package sample.controller;
            import org.springframework.security.crypto.password.PasswordEncoder;
            public class AuthController {
                private PasswordEncoder encoder;
                public String login(String raw, String encoded) {
                    if (isMatchPassword(raw, encoded)) return "token";
                    throw new IllegalArgumentException("wrong password");
                }
                private boolean isMatchPassword(String raw, String encoded) {
                    return encoder.matches(raw, encoded);
                }
            }
            """);
        write("org/springframework/security/crypto/password/PasswordEncoder.java", """
            package org.springframework.security.crypto.password;
            public interface PasswordEncoder { boolean matches(CharSequence raw, String encoded); }
            """);
        SourceTrace trace = new SourceTrace("src/main/java/sample/controller/AuthController.java", 5, 8);
        RequestBinding raw = new RequestBinding("raw", BindingLocation.BODY, "String", true,
            null, null, null, List.of(), trace);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/login", "sample.controller.AuthController", "login",
            List.of(raw), new ResponseBinding("String", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 2, List.of(), null);

        var build = new io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder().build(
            scan, source(), io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget.defaults());
        EndpointRuleOutput output = new io.atworks.specscan.analysis.application.RuleOutputService()
            .generate(scan, build, List.of()).get("POST /login");

        assertThat(output.excludedBusinessRules()).withFailMessage("output=%s graph=%s", output,
            build.graphs().get(0)).anySatisfy(rule -> {
            assertThat(rule.ruleId()).isEqualTo("SPRING_SECURITY_PASSWORD_MATCH_FAILURE");
            assertThat(rule.operator()).isEqualTo("PASSWORD_MATCH");
        });
    }

    @Test void detectsQualifiedPositiveMatchWithTrailingFailureReturn() throws Exception {
        write("sample/controller/AuthController.java", """
            package sample.controller;
            import org.springframework.security.crypto.password.PasswordEncoder;
            public class AuthController {
                private PasswordEncoder encoder;
                public String login(String raw, String encoded) {
                    if (encoder.matches(raw, encoded)) return "token";
                    return "error";
                }
            }
            """);
        write("org/springframework/security/crypto/password/PasswordEncoder.java", """
            package org.springframework.security.crypto.password;
            public interface PasswordEncoder { boolean matches(CharSequence raw, String encoded); }
            """);
        SourceTrace trace = new SourceTrace("src/main/java/sample/controller/AuthController.java", 5, 8);
        RequestBinding raw = new RequestBinding("raw", BindingLocation.BODY, "String", true,
            null, null, null, List.of(), trace);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/login", "sample.controller.AuthController", "login",
            List.of(raw), new ResponseBinding("String", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 2, List.of(), null);

        EndpointRuleOutput output = TestRuleOutputs.generate(scan, source()).get("POST /login");

        assertThat(output.excludedBusinessRules()).anySatisfy(rule -> {
            assertThat(rule.ruleId()).isEqualTo("SPRING_SECURITY_PASSWORD_MATCH_FAILURE");
            assertThat(rule.category().name()).isEqualTo("AUTHENTICATION");
            assertThat(rule.evidence()).isNotEmpty();
        });
        assertThat(output.responseAssertions()).noneSatisfy(assertion ->
            assertThat(assertion.targetPath()).isEqualTo("$.result"));
    }

    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private RepositorySource source() {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 2, 2, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(2, 1, 1, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 2, "LOCAL", "main", 0));
    }
}
