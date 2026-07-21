package io.atworks.apiintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.atworks.apiintelligence.config.ApiIntelligenceConfiguration;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiIntelligenceAnalysisServiceE2ETest {

    @TempDir
    Path temp;

    @Test
    void realEstateReadOnlySmokeWhenAvailable() throws Exception {
        Path p = Path.of("D:/workspace/real-estate/RealEstate");
        if (!Files.isDirectory(p)) {
            return;
        }
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        s.createContext("/", e -> {
            byte[] b = "{\"result\":{}}".getBytes();
            e.sendResponseHeaders(200, b.length);
            e.getResponseBody().write(b);
            e.close();
        });
        s.start();
        try {
            var c = new ApiIntelligenceConfiguration(temp.resolve("out"),
                new ApiIntelligenceConfiguration.GraphSettings(2, 1000, 2000),
                new ApiIntelligenceConfiguration.OpenAiSettings("x",
                    URI.create("http://localhost:" + s.getAddress().getPort() + "/"), "gpt-4o-mini",
                    10, 10, 0, 1, 5, 10000));
            new ApiIntelligenceAnalysisService().analyze(p, c);
        } finally {
            s.stop(0);
        }
    }

    @Test
    void localFixtureProducesApiGraphEvidenceAndOneModelCall() throws Exception {
        Path f = temp.resolve("DemoController.java");
        Files.writeString(f,
            "@org.springframework.web.bind.annotation.RestController class DemoController { @org.springframework.web.bind.annotation.GetMapping(\"/demo\") public String demo(){ return \"ok\"; }}");
        HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
        int[] calls = {0};
        s.createContext("/", e -> {
            calls[0]++;
            byte[] b = "{\"result\":{\"additionalAnalysis\":[]}}".getBytes();
            e.sendResponseHeaders(200, b.length);
            e.getResponseBody().write(b);
            e.close();
        });
        s.start();
        try {
            var c = new ApiIntelligenceConfiguration(temp.resolve("out"),
                new ApiIntelligenceConfiguration.GraphSettings(4, 100, 200),
                new ApiIntelligenceConfiguration.OpenAiSettings("x",
                    URI.create("http://localhost:" + s.getAddress().getPort() + "/"), "gpt-4o-mini",
                    10, 10, 0, 1, 5, 10000));
            var r = new ApiIntelligenceAnalysisService().analyze(temp, c);
            assertThat((Map<?, ?>) r.get("graphs")).isNotEmpty();
            assertThat(calls[0]).isEqualTo(1);
        } finally {
            s.stop(0);
        }
    }
}
