package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256, the only thing standing between a truncated download and a browser that will not start. */
public final class Checksum {

    private static final int BUFFER = 64 * 1024;

    private Checksum() {
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return sha256(in);
        }
    }

    public static String sha256(InputStream in) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER];
        int read;
        while ((read = in.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Compares without leaking where two values start to differ.
     *
     * <p>Overkill for a download, and free: it costs one method and removes the question.
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("this JVM has no SHA-256", e);
        }
    }
}
