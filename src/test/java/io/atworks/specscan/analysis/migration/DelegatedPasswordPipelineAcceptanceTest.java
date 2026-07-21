package io.atworks.specscan.analysis.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DelegatedPasswordPipelineAcceptanceTest {
    @TempDir Path workspace;

    @Test void followsRequestThroughOptionalLambdaAndBooleanHelperToPasswordMatch() throws Exception {
        write("sample/controller/SessionController.java", """
            package sample.controller;
            import sample.dto.LoginCommand;
            import sample.service.SessionService;
            public class SessionController {
                private SessionService service;
                public Object login(LoginCommand request) {
                    String uid = request.getUid();
                    String password = request.getPassword();
                    return service.login(uid, password);
                }
            }
            """);
        write("sample/dto/LoginCommand.java", """
            package sample.dto;
            @lombok.Getter
            public class LoginCommand {
                String uid; String password;
            }
            """);
        write("sample/service/SessionService.java", """
            package sample.service;
            import sample.domain.*;
            import java.util.Optional;
            import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
            public class SessionService {
                private AccountRepository repository;
                private BCryptPasswordEncoder encoder;
                public LoginResult login(String uid, String password) {
                    return repository.findByUid(uid)
                        .map(account -> loginIfMatch(account, password))
                        .orElse(new LoginResult());
                }
                private LoginResult loginIfMatch(Account account, String password) {
                    if (passwordMatches(password, account)) return new LoginResult();
                    throw new IllegalArgumentException("wrong password");
                }
                private boolean passwordMatches(String raw, Account account) {
                    return encoder.matches(raw, account.getEncodedPassword());
                }
            }
            """);
        write("sample/domain/Account.java", """
            package sample.domain;
            public class Account { public String getEncodedPassword() { return "encoded"; } }
            """);
        write("sample/domain/AccountRepository.java", """
            package sample.domain;
            import java.util.Optional;
            public interface AccountRepository { Optional<Account> findByUid(String uid); }
            """);
        write("sample/domain/LoginResult.java", """
            package sample.domain;
            public class LoginResult {}
            """);
        SourceTrace trace = new SourceTrace(
            "src/main/java/sample/controller/SessionController.java", 6, 8);
        RequestBinding body = new RequestBinding("request", BindingLocation.BODY,
            "sample.dto.LoginCommand", true, null, null, null, List.of(), trace);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/sessions", "sample.controller.SessionController",
            "login", List.of(body), new ResponseBinding("Object", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 6, List.of(), null);

        EndpointRuleOutput output = TestRuleOutputs.generate(scan, source()).get("POST /sessions");

        assertThat(output.excludedBusinessRules()).withFailMessage("output=%s", output)
            .anySatisfy(rule -> {
                assertThat(rule.ruleId()).isEqualTo("SPRING_SECURITY_PASSWORD_MATCH_FAILURE");
                assertThat(rule.targetPath()).isEqualTo("$.password");
                assertThat(rule.operator()).isEqualTo("PASSWORD_MATCH");
            });
        assertThat(output.diagnostics()).noneMatch(diagnostic ->
            diagnostic.code().equals("SEMANTIC_UNRESOLVED"));
    }

    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private RepositorySource source() {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 6, 6, 1,
                "DETECTED")), "Gradle", new JavaInventorySummary(6, 1, 1, true, 0), List.of(),
            List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 8, "LOCAL", "main", 0));
    }
}
