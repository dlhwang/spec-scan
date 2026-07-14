package io.atworks.specscan.analysis.domain.output;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import java.util.Locale;
import java.util.Objects;

public record OperationKey(String httpMethod, String path) {
    public OperationKey {
        httpMethod = Objects.requireNonNull(httpMethod).trim().toUpperCase(Locale.ROOT);
        path = normalizePath(path);
        if (httpMethod.isBlank()) throw new IllegalArgumentException("httpMethod is required");
    }

    public static OperationKey of(ApiEndpoint endpoint) {
        return new OperationKey(endpoint.httpMethod(), endpoint.path());
    }

    public String externalKey() {
        return httpMethod + " " + path;
    }

    private static String normalizePath(String value) {
        String path = Objects.requireNonNull(value).trim();
        if (path.isBlank()) throw new IllegalArgumentException("path is required");
        if (!path.startsWith("/")) path = "/" + path;
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
