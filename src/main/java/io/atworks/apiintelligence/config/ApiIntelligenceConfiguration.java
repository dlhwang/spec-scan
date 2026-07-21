package io.atworks.apiintelligence.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public record ApiIntelligenceConfiguration(Path outputRoot, GraphSettings graph,
                                           OpenAiSettings openai) {

    public ApiIntelligenceConfiguration {
        outputRoot = Objects.requireNonNull(outputRoot, "outputRoot").normalize();
        graph = Objects.requireNonNull(graph, "graph");
        openai = Objects.requireNonNull(openai, "openai");
    }

    public record GraphSettings(int maxDepth, int maxMethods, int maxEdges) {

        public GraphSettings {
            positive(maxDepth, "graph.max-depth");
            positive(maxMethods, "graph.max-methods");
            positive(maxEdges, "graph.max-edges");
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static final class OpenAiSettings {

        @JsonIgnore
        private final String apiKey;
        private final URI endpoint;
        private final String model;
        private final int requestTimeoutSeconds;
        private final int connectTimeoutSeconds;
        private final int maxRetries;
        private final int maxConcurrency;
        private final int retryAfterCapSeconds;
        private final int maxInputCharacters;

        public OpenAiSettings(String apiKey, URI endpoint, String model, int requestTimeoutSeconds,
            int connectTimeoutSeconds, int maxRetries, int maxConcurrency, int retryAfterCapSeconds,
            int maxInputCharacters) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(
                endpoint.getScheme()))) {
                throw new IllegalArgumentException("openai.endpoint must use HTTP or HTTPS");
            }
            this.model = require(model, "openai.model");
            positive(requestTimeoutSeconds, "openai.request-timeout-seconds");
            positive(connectTimeoutSeconds, "openai.connect-timeout-seconds");
            if (maxRetries < 0) {
                throw new IllegalArgumentException("openai.max-retries must not be negative");
            }
            positive(maxConcurrency, "openai.max-concurrency");
            positive(retryAfterCapSeconds, "openai.retry-after-cap-seconds");
            positive(maxInputCharacters, "openai.max-input-characters");
            this.requestTimeoutSeconds = requestTimeoutSeconds;
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            this.maxRetries = maxRetries;
            this.maxConcurrency = maxConcurrency;
            this.retryAfterCapSeconds = retryAfterCapSeconds;
            this.maxInputCharacters = maxInputCharacters;
        }

        @JsonIgnore
        public String apiKey() {
            return apiKey;
        }

        @JsonIgnore
        public boolean configured() {
            return !apiKey.isBlank();
        }

        public URI endpoint() {
            return endpoint;
        }

        public String model() {
            return model;
        }

        public int requestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public int connectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public int maxRetries() {
            return maxRetries;
        }

        public int maxConcurrency() {
            return maxConcurrency;
        }

        public int retryAfterCapSeconds() {
            return retryAfterCapSeconds;
        }

        public int maxInputCharacters() {
            return maxInputCharacters;
        }

        @Override
        public String toString() {
            return "OpenAiSettings[apiKey=<redacted>, endpoint=" + endpoint + ", model=" + model
                + ", requestTimeoutSeconds=" + requestTimeoutSeconds + ", connectTimeoutSeconds="
                + connectTimeoutSeconds + ", maxRetries=" + maxRetries + ", maxConcurrency="
                + maxConcurrency + ", retryAfterCapSeconds=" + retryAfterCapSeconds
                + ", maxInputCharacters=" + maxInputCharacters + "]";
        }
    }

    private static void positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is blank");
        }
        return value.trim();
    }
}
