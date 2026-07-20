package io.specscan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.specscan.extract.EndpointExtractor;
import io.specscan.graph.GraphBuilder;
import io.specscan.model.ApiEndpoint;
import io.specscan.model.Condition;
import io.specscan.model.Operator;
import io.specscan.parse.ProjectIndex;
import io.specscan.parse.ProjectParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end scan over a synthetic Spring project covering the core extraction rules. */
class SpecScanTest {

    @TempDir
    static Path projectDir;

    static List<ApiEndpoint> endpoints;

    @BeforeAll
    static void scanFixture() throws IOException {
        Path src = projectDir.resolve("src/main/java/demo");
        Files.createDirectories(src);

        write(src, "LoginRequest.java", """
                package demo;
                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.Size;
                public class LoginRequest {
                    @NotBlank
                    String username;
                    @Size(min = 8, max = 30)
                    String password;
                    public String getUsername() { return username; }
                    public String getPassword() { return password; }
                }
                """);
        write(src, "LoginResponse.java", """
                package demo;
                public class LoginResponse {
                    String token;
                    long expiresIn;
                    public LoginResponse(String token, long expiresIn) {
                        this.token = token;
                        this.expiresIn = expiresIn;
                    }
                }
                """);
        write(src, "AuthService.java", """
                package demo;
                public class AuthService {
                    public LoginResponse login(LoginRequest request) {
                        if (request.getUsername() == null) {
                            throw new IllegalArgumentException("아이디는 필수입니다.");
                        }
                        if (request.getPassword().length() < 10) {
                            throw new IllegalArgumentException("비밀번호는 최소 10자입니다.");
                        }
                        return new LoginResponse(issue(request), 3600L);
                    }
                    String issue(LoginRequest request) { return java.util.UUID.randomUUID().toString(); }
                }
                """);
        write(src, "AuthController.java", """
                package demo;
                import jakarta.validation.Valid;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/api/auth")
                public class AuthController {
                    private final AuthService service = new AuthService();

                    @PostMapping("/login")
                    public ResponseEntity<LoginResponse> login(
                            @RequestHeader("X-Device-Id") String deviceId,
                            @RequestParam(required = false, defaultValue = "web") String channel,
                            @Valid @RequestBody LoginRequest request) {
                        return ResponseEntity.ok(service.login(request));
                    }
                }
                """);

        // MVC view controller + error-accumulating validator (DDD style)
        write(src, "SignupForm.java", """
                package demo;
                public class SignupForm {
                    String email;
                    Profile profile;
                    public String getEmail() { return email; }
                    public Profile getProfile() { return profile; }
                    public static class Profile {
                        String nickname;
                        public String getNickname() { return nickname; }
                    }
                }
                """);
        write(src, "ValidationError.java", """
                package demo;
                public class ValidationError {
                    public static ValidationError of(String name, String code) { return new ValidationError(); }
                }
                """);
        write(src, "SignupValidator.java", """
                package demo;
                import java.util.ArrayList;
                import java.util.List;
                import org.springframework.util.StringUtils;
                public class SignupValidator {
                    public List<ValidationError> validate(SignupForm form) {
                        List<ValidationError> errors = new ArrayList<>();
                        if (!StringUtils.hasText(form.getEmail()))
                            errors.add(ValidationError.of("email", "required"));
                        if (form.getProfile() == null) {
                            errors.add(ValidationError.of("profile", "required"));
                        } else {
                            if (!StringUtils.hasText(form.getProfile().getNickname()))
                                errors.add(ValidationError.of("profile.nickname", "required"));
                        }
                        return errors;
                    }
                }
                """);
        write(src, "SignupController.java", """
                package demo;
                import java.util.List;
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.ModelAttribute;
                import org.springframework.web.bind.annotation.PostMapping;

                @Controller
                public class SignupController {
                    @PostMapping("/signup")
                    public String signup(@ModelAttribute SignupForm form) {
                        List<ValidationError> errors = new SignupValidator().validate(form);
                        if (!errors.isEmpty()) return "signup/form";
                        return "signup/done";
                    }
                }
                """);

        ProjectIndex index = ProjectParser.parse(projectDir);
        endpoints = new EndpointExtractor(index, GraphBuilder.build(index)).extract();
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    private ApiEndpoint login() {
        return endpoints.stream().filter(e -> e.path.equals("/api/auth/login")).findFirst().orElseThrow();
    }

    @Test
    void extractsEndpointSpec() {
        ApiEndpoint ep = login();
        assertEquals("POST", ep.httpMethod);
        assertEquals(200, ep.successStatus);
        assertEquals("demo.LoginRequest", ep.requestBodyType);
        assertEquals("demo.LoginResponse", ep.responseBodyType);
        assertEquals(1, ep.headers.size());
        assertEquals("X-Device-Id", ep.headers.get(0).name());
        assertEquals("channel", ep.queryParams.get(0).name());
        assertEquals("web", ep.queryParams.get(0).defaultValue());
        assertNotNull(ep.requestBody.fields.get("password"));
    }

    @Test
    void beanValidationBecomesPreConditions() {
        List<Condition> pre = login().preConditions;
        assertTrue(has(pre, "$.password", Operator.GOE, 8), describe(pre));
        assertTrue(has(pre, "$.password", Operator.LOE, 30), describe(pre));
        assertTrue(has(pre, "$.username", Operator.NOT_BLANK, null), describe(pre));
    }

    @Test
    void ifThrowBecomesPreConditions() {
        List<Condition> pre = login().preConditions;
        assertTrue(has(pre, "$.username", Operator.NOT_NULL, null), describe(pre));
        // if (password.length() < 10) throw  ->  {$.password, goe, 10}
        assertTrue(pre.stream().anyMatch(c -> c.path().equals("$.password")
                && c.operator() == Operator.GOE && "if-throw".equals(c.source())), describe(pre));
    }

    @Test
    void responseAssertionsFromConstruction() {
        List<Condition> resp = login().responseAssertions;
        assertTrue(has(resp, "$.token", Operator.NOT_EMPTY, null), describe(resp));
        assertTrue(has(resp, "$.expiresIn", Operator.EQ, 3600L), describe(resp));
    }

    @Test
    void errorAccumulatingValidatorAndViewEndpoint() {
        ApiEndpoint ep = endpoints.stream().filter(e -> e.path.equals("/signup")).findFirst().orElseThrow();
        assertEquals("application/x-www-form-urlencoded", ep.consumes);
        assertEquals("text/html (view)", ep.responseBodyType);
        assertTrue(ep.queryParams.stream().anyMatch(q -> q.name().equals("profile.nickname")), "form params flattened");

        List<Condition> pre = ep.preConditions;
        assertTrue(has(pre, "$.email", Operator.NOT_BLANK, null), describe(pre));
        assertTrue(has(pre, "$.profile", Operator.NOT_NULL, null), describe(pre));
        assertTrue(has(pre, "$.profile.nickname", Operator.NOT_BLANK, null), describe(pre));

        List<Condition> resp = ep.responseAssertions;
        assertTrue(resp.stream().anyMatch(c -> c.path().equals("$view")), describe(resp));
    }

    private static boolean has(List<Condition> conds, String path, Operator op, Object expectedNum) {
        return conds.stream().anyMatch(c -> c.path().equals(path) && c.operator() == op
                && (expectedNum == null || String.valueOf(expectedNum).equals(String.valueOf(c.expected()))));
    }

    private static String describe(List<Condition> conds) {
        StringBuilder sb = new StringBuilder("conditions were:\n");
        conds.forEach(c -> sb.append("  ").append(c).append('\n'));
        return sb.toString();
    }
}
