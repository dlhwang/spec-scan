package io.atworks.apiintelligence.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class ApiIntelligenceConfigurationLoader {

    private static final String ROOT = "api-intelligence";
    private static final Set<String> ROOT_KEYS = Set.of("output-root", "graph", "openai");
    private static final Set<String> GRAPH_KEYS = Set.of("max-depth", "max-methods", "max-edges");
    private static final Set<String> OPENAI_KEYS = Set.of("api-key", "endpoint", "model",
        "request-timeout-seconds",
        "connect-timeout-seconds", "max-retries", "max-concurrency", "retry-after-cap-seconds",
        "max-input-characters");

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public ApiIntelligenceConfiguration load() {
        return load(Path.of("."), System.getenv());
    }

    public ApiIntelligenceConfiguration load(Path workingDirectory,
        Map<String, String> environment) {
        try {
            ObjectNode merged = readBase();
            Path local = workingDirectory.resolve("application-local.yml");
            if (Files.isRegularFile(local)) {
                merge(merged, read(local));
            } else {
                Path resourceLocal = workingDirectory.resolve(
                    "src/main/resources/application-local.yml");
                if (Files.isRegularFile(resourceLocal)) {
                    merge(merged, read(resourceLocal));
                }
            }
            validateKeys(merged);
            ObjectNode root = requiredObject(merged, ROOT);
            ObjectNode graph = requiredObject(root, "graph");
            ObjectNode openai = requiredObject(root, "openai");
            String key = environment.getOrDefault("OPENAI_API_KEY", text(openai, "api-key", ""));
            return new ApiIntelligenceConfiguration(
                workingDirectory.resolve(requiredText(root, "output-root")).normalize(),
                new ApiIntelligenceConfiguration.GraphSettings(integer(graph, "max-depth"),
                    integer(graph, "max-methods"), integer(graph, "max-edges")),
                new ApiIntelligenceConfiguration.OpenAiSettings(key,
                    URI.create(requiredText(openai, "endpoint")),
                    requiredText(openai, "model"), integer(openai, "request-timeout-seconds"),
                    integer(openai, "connect-timeout-seconds"), integer(openai, "max-retries"),
                    integer(openai, "max-concurrency"), integer(openai, "retry-after-cap-seconds"),
                    integer(openai, "max-input-characters")));
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("CONFIGURATION_INVALID", safeMessage(e));
        }
    }

    private ObjectNode readBase() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            if (in == null) {
                throw new ConfigurationException("CONFIGURATION_MISSING",
                    "application.yml is missing");
            }
            return requireObject(yaml.readTree(in), "application.yml");
        }
    }

    private ObjectNode read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return requireObject(yaml.readTree(in), path.toString());
        }
    }

    private void validateKeys(ObjectNode document) {
        rejectUnknown(document, Set.of(ROOT), "root");
        ObjectNode root = requiredObject(document, ROOT);
        rejectUnknown(root, ROOT_KEYS, ROOT);
        rejectUnknown(requiredObject(root, "graph"), GRAPH_KEYS, ROOT + ".graph");
        rejectUnknown(requiredObject(root, "openai"), OPENAI_KEYS, ROOT + ".openai");
    }

    private static void rejectUnknown(ObjectNode node, Set<String> allowed, String path) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw new ConfigurationException("CONFIGURATION_UNKNOWN_KEY",
                    "Unknown configuration key: " + path + "." + field);
            }
        }
    }

    private static void merge(ObjectNode target, ObjectNode override) {
        override.fields().forEachRemaining(entry -> {
            JsonNode current = target.get(entry.getKey());
            if (current instanceof ObjectNode currentObject
                && entry.getValue() instanceof ObjectNode overrideObject) {
                merge(currentObject, overrideObject);
            } else {
                target.set(entry.getKey(), entry.getValue());
            }
        });
    }

    private static ObjectNode requiredObject(JsonNode parent, String name) {
        JsonNode value = parent == null ? null : parent.get(name);
        if (!(value instanceof ObjectNode object)) {
            throw new ConfigurationException("CONFIGURATION_INVALID",
                "Configuration object is missing: " + name);
        }
        return object;
    }

    private static ObjectNode requireObject(JsonNode value, String name) {
        if (!(value instanceof ObjectNode object)) {
            throw new ConfigurationException("CONFIGURATION_INVALID",
                "Configuration document is not an object: " + name);
        }
        return object;
    }

    private static String requiredText(ObjectNode node, String name) {
        String value = text(node, name, null);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("CONFIGURATION_INVALID",
                "Configuration text is missing: " + name);
        }
        return value;
    }

    private static String text(ObjectNode node, String name, String fallback) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isTextual()) {
            throw new ConfigurationException("CONFIGURATION_INVALID",
                "Configuration value must be text: " + name);
        }
        return value.textValue();
    }

    private static int integer(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber()) {
            throw new ConfigurationException("CONFIGURATION_INVALID",
                "Configuration value must be an integer: " + name);
        }
        return value.intValue();
    }

    private static String safeMessage(Exception e) {
        return e instanceof IllegalArgumentException ? e.getMessage()
            : "Unable to load API Intelligence configuration";
    }
}
