package io.atworks.specscan.analysis.support.fact;

import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.fact.SourceRange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DeterministicFactNodeIdGenerator {
    /**
     * Generates a deterministic node ID from the canonical tuple (type, owner, range, discriminator).
     * The discriminator distinguishes structurally different nodes that share the same (type, owner, range).
     * It must NOT be an edge-context value such as "LEFT", "RIGHT", or "RECEIVER" — those belong on the edge.
     */
    public String generate(FactNodeType type, String owner, SourceRange range, String discriminator) {
        String canonical = String.join("|", type.name(), range.relativePath(), value(owner),
            range.startLine() + ":" + range.startColumn(), range.endLine() + ":" + range.endColumn(), value(discriminator));
        return type.name() + ":" + hash(canonical).substring(0, 24);
    }
    public String edgeId(String source, String target, String type, int ordinal, String role) {
        return "EDGE:" + hash(String.join("|", source, target, type, Integer.toString(ordinal), value(role))).substring(0, 24);
    }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private String value(String value) { return value == null ? "" : value.replace('\\', '/'); }
}
