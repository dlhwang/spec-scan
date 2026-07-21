package io.atworks.apiintelligence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiIntelligenceConfigurationLoaderTest {

    @TempDir
    Path temp;
    private final ApiIntelligenceConfigurationLoader loader = new ApiIntelligenceConfigurationLoader();

    @Test
    void loadsDefaultsWithoutApiKey() {
        ApiIntelligenceConfiguration config = loader.load(temp, Map.of());
        assertThat(config.outputRoot()).isEqualTo(temp.resolve("build/api-intelligence-runs"));
        assertThat(config.graph().maxDepth()).isEqualTo(8);
        assertThat(config.openai().model()).isEqualTo("gpt-4o-mini");
        assertThat(config.openai().configured()).isFalse();
    }

    @Test
    void localOverridesBaseAndEnvironmentOverridesKey() throws Exception {
        writeLocal("""
            api-intelligence:
              graph:
                max-depth: 3
              openai:
                api-key: local-secret
                max-concurrency: 1
            """);
        ApiIntelligenceConfiguration config = loader.load(temp,
            Map.of("OPENAI_API_KEY", "env-secret"));
        assertThat(config.graph().maxDepth()).isEqualTo(3);
        assertThat(config.openai().maxConcurrency()).isEqualTo(1);
        assertThat(config.openai().apiKey()).isEqualTo("env-secret");
    }

    @Test
    void rejectsUnknownAndInvalidValues() throws Exception {
        writeLocal("""
            api-intelligence:
              unknown: true
            """);
        assertThatThrownBy(() -> loader.load(temp, Map.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unknown configuration key");

        writeLocal("""
            api-intelligence:
              openai:
                max-concurrency: 0
            """);
        assertThatThrownBy(() -> loader.load(temp, Map.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("max-concurrency");
    }

    @Test
    void neverSerializesOrPrintsApiKey() throws Exception {
        String secret = "uow02-super-secret";
        ApiIntelligenceConfiguration config = loader.load(temp, Map.of("OPENAI_API_KEY", secret));
        assertThat(config.toString()).doesNotContain(secret).contains("<redacted>");
        assertThat(new ObjectMapper().writeValueAsString(config)).doesNotContain(secret);
        assertThat(config.openai().configured()).isTrue();
    }

    private void writeLocal(String value) throws Exception {
        Files.writeString(temp.resolve("application-local.yml"), value, StandardCharsets.UTF_8);
    }
}
