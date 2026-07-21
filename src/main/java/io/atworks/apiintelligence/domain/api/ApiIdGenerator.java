package io.atworks.apiintelligence.domain.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class ApiIdGenerator {

    private ApiIdGenerator() {
    }

    public static String generate(String method, String path, String controller, String signature) {
        return sha256(
            req(method).toUpperCase(Locale.ROOT) + "\n" + normalizePath(path) + "\n" + req(
                controller) + "\n" + req(signature));
    }

    public static String normalizePath(String value) {
        String p = req(value).replace('\\', '/').replaceAll("/+", "/");
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String req(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("identity value is blank");
        }
        return v.trim();
    }
}
