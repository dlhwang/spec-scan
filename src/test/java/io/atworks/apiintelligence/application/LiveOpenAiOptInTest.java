package io.atworks.apiintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.apiintelligence.adapter.openai.OpenAiIntelligenceAdapter;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveOpenAiOptInTest {

    @Test
    void liveCallIsExplicitlyOptIn() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            return;
        }
        var adapter = new OpenAiIntelligenceAdapter(
            URI.create("https://api.openai.com/v1/responses"), key, "gpt-4o-mini");
        var result = adapter.analyze("opt-in",
            new CodeGraph("opt-in", List.of(), List.of(), List.of()), List.of());
        assertThat(result).isNotNull();
    }
}
