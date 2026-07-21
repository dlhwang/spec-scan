package io.atworks.specscan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.atworks.apiintelligence.application.ApiIntelligenceAnalysisService;
import io.atworks.apiintelligence.config.ApiIntelligenceConfigurationLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpecScanWebServer {

    private static final Logger log = LoggerFactory.getLogger(SpecScanWebServer.class);
    private static final int DEFAULT_PORT = 8088;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", SpecScanWebServer::handleIndex);
        server.createContext("/api/scan", SpecScanWebServer::handleScan);
        server.createContext("/api/intelligence", SpecScanWebServer::handleIntelligence);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Spec Scan Web UI: http://localhost:" + port);
    }

    private static void handleIntelligence(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }
        try {
            JsonNode request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            String path = optionalText(request, "projectPath");
            String gitUrl = optionalText(request, "gitUrl");
            var config = new ApiIntelligenceConfigurationLoader().load();
            if (!config.openai().configured()) {
                throw new IllegalArgumentException("OPENAI_API_KEY is not configured");
            }
            io.atworks.apiintelligence.domain.source.AnalysisSource source;
            if (path != null) {
                source = new io.atworks.apiintelligence.domain.source.LocalSource(Path.of(path));
            } else if (gitUrl != null) {
                String rev = optionalText(request, "revision");
                String type = optionalText(request, "revisionType");
                source = new io.atworks.apiintelligence.domain.source.GitSource(
                    java.net.URI.create(gitUrl), rev == null ? null
                    : io.atworks.apiintelligence.domain.source.RevisionType.valueOf(
                        type.toUpperCase()), rev);
            } else {
                throw new IllegalArgumentException("Missing required field: projectPath or gitUrl");
            }
            sendJson(exchange, 200, new ApiIntelligenceAnalysisService().analyze(source, config));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500,
                Map.of("error", "Intelligence analysis failed", "message", e.getMessage()));

            log.error("Intelligence analysis failed", e);
        }
    }

    private static int parsePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        return DEFAULT_PORT;
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }

        try (var is = SpecScanWebServer.class.getResourceAsStream("/static" + path)) {
            if (is == null) {
                send(exchange, 404, "text/plain", "Not Found");
                return;
            }
            byte[] bytes = is.readAllBytes();
            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().flush();
            exchange.close();
        } catch (Exception e) {
            send(exchange, 500, "text/plain", "Internal Server Error: " + e.getMessage());
        }
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        return "text/plain";
    }

    private static void handleScan(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            JsonNode request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            String repositorySource = requiredText(request, "repositorySource", "repositoryUrl");
            requiredText(request, "projectName");
            requiredText(request, "baseUrl");
            String revisionType = optionalText(request, "revisionType");
            String revision = optionalText(request, "revision");
            String analysisMode = optionalText(request, "analysisMode");

            String result;
            if ("LLM".equalsIgnoreCase(analysisMode)) {
                var config = new io.atworks.apiintelligence.config.ApiIntelligenceConfigurationLoader().load();
                if (!config.openai().configured()) {
                    throw new IllegalArgumentException("OPENAI_API_KEY is not configured");
                }
                result = new GitExecutionSpecScanService().scanWithLlmArtifacts(repositorySource,
                    revisionType, revision, config);
            } else {
                result = new GitExecutionSpecScanService().scanWithArtifacts(repositorySource,
                    revisionType, revision);
            }
            send(exchange, 200, "application/json; charset=utf-8", result);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Scan failed", e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Scan failed");
            body.put("message", e.getMessage());
            sendJson(exchange, 500, body);
        }
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String fieldName, String fallbackFieldName) {
        String value = optionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            value = optionalText(node, fallbackFieldName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", OBJECT_MAPPER.writeValueAsString(body));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try {
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            exchange.close();
        }
    }

    private static String indexHtml() {
        return "";
    }
}
