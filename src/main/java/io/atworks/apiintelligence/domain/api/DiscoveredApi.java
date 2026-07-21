package io.atworks.apiintelligence.domain.api;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record DiscoveredApi(String apiId, String httpMethod, String path, String controllerType,
                            String handlerSignature, List<String> requestBindings,
                            String responseType, SourceLocation sourceLocation) {

    public DiscoveredApi {
        httpMethod = req(httpMethod).toUpperCase(Locale.ROOT);
        path = ApiIdGenerator.normalizePath(path);
        controllerType = req(controllerType);
        handlerSignature = req(handlerSignature);
        responseType = req(responseType);
        sourceLocation = Objects.requireNonNull(sourceLocation);
        requestBindings = requestBindings == null ? List.of()
            : requestBindings.stream().map(DiscoveredApi::req).distinct().sorted().toList();
        if (!ApiIdGenerator.generate(httpMethod, path, controllerType, handlerSignature)
            .equals(apiId)) {
            throw new IllegalArgumentException("apiId mismatch");
        }
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("API value is blank");
        }
        return v.trim();
    }
}
