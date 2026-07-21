package io.atworks.apiintelligence.adapter.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.intelligence.IntelligenceItem;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenAiIntelligenceAdapterTest {

    private static final Logger log = LoggerFactory.getLogger(OpenAiIntelligenceAdapterTest.class);

    @Test
    @DisplayName("Markdown fenced JSON과 객체형 분석 결과를 파싱한다")
    void parsesFencedJsonObjectResponse() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        s.createContext("/", e -> {
            byte[] b = "{\"output_text\":\"Here's the analysis:\\n```json\\n{\\\"preConditions\\\":{\\\"auth\\\":\\\"required\\\"}}\\n```\"}".getBytes(
                StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, b.length);
            e.getResponseBody().write(b);
            e.close();
        });
        s.start();
        try {
            var r = new OpenAiIntelligenceAdapter(
                URI.create("http://localhost:" + s.getAddress().getPort() + "/"), "secret",
                "gpt-4o-mini").analyze("api-1",
                new CodeGraph("api-1", List.of(), List.of(), List.of()), List.of());
            log.info(r.toString());
            assertThat(r.preConditions())
                .hasSize(1)
                .first()
                .extracting(IntelligenceItem::description)
                .isEqualTo("auth: required");
        } finally {
            s.stop(0);
        }
    }

    @Test
    @DisplayName("API 하나의 그래프와 Evidence를 단일 요청으로 전송하고 결과를 파싱한다")
    void sendsSingleApiContextAndParsesResult() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        List<String> seen = new ArrayList<>();
        s.createContext("/", e -> {
            seen.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = "{\"result\":{\"preConditions\":[{\"description\":\"auth\",\"category\":\"security\",\"evidenceIds\":[\"ev-1\"],\"confidence\":0.9}]}}".getBytes(
                StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, b.length);
            e.getResponseBody().write(b);
            e.close();
        });
        s.start();
        try {
            var r = new OpenAiIntelligenceAdapter(
                URI.create("http://localhost:" + s.getAddress().getPort() + "/"), "secret",
                "gpt-4o-mini").analyze("api-1",
                new CodeGraph("api-1", List.of(), List.of(), List.of()), List.of());
            log.info(r.toString());
            assertThat(r.preConditions()).hasSize(1);
            assertThat(seen).hasSize(1);
            assertThat(seen.get(0)).contains("api-1");
            assertThat(seen.get(0)).contains(
                "Return exactly one valid JSON object and nothing else");
        } finally {
            s.stop(0);
        }
    }
}
