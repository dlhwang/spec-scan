package io.atworks.specscan.analysis.support.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CorpusIntegrityVerifier {
    public String digest(CorpusAccessGuard.ConfinedCorpusView view) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String path : view.listSourcePaths()) {
                digest.update(path.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(view.readSource(path));
                digest.update((byte) 0xff);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public boolean matches(CorpusAccessGuard.ConfinedCorpusView view, String expectedDigest) {
        return digest(view).equals(expectedDigest);
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
