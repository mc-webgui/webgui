package land.webgui.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the security-critical {@link WebviewSignedToken#verify} path: a token
 * signed with the configured secret must verify, and any tampering, expiry, or
 * malformed input must be rejected.
 *
 * <p>Signing normally happens via {@code create(ServerPlayerEntity)} which needs
 * a live player, so these tests forge tokens with the same HMAC scheme and a
 * secret injected into {@link WebviewServerConfig} by reflection.
 *
 * <p>Tagged {@code "loader"}: touching {@link WebviewServerConfig} requires the
 * mod-loader runtime on the classpath (Fabric loom provides it for unit tests;
 * NeoForge's moddev does not), so this suite is excluded from the NeoForge
 * {@code test} run. The verification logic itself is loader-independent.
 */
@Tag("loader")
class WebviewSignedTokenTest {

    private static final byte[] SECRET = new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
    };

    @BeforeEach
    void injectSecret() throws Exception {
        setConfigSecret(Base64.getEncoder().encodeToString(SECRET));
    }

    /** Reflectively set WebviewServerConfig.instance.tokenSecretBase64. */
    private static void setConfigSecret(String base64) throws Exception {
        Field instanceField = WebviewServerConfig.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object instance = instanceField.get(null);
        Field secretField = WebviewServerConfig.class.getDeclaredField("tokenSecretBase64");
        secretField.setAccessible(true);
        secretField.set(instance, base64);
    }

    /** Builds a token the way the mod does: base64url(payload) + "." + base64url(hmac(payload)). */
    private static String forgeToken(UUID uuid, long expEpochSeconds) throws Exception {
        String payload = "1|" + uuid + "|" + expEpochSeconds;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        byte[] sig = mac.doFinal(payloadBytes);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(payloadBytes) + "." + enc.encodeToString(sig);
    }

    private static long future() {
        return Instant.now().getEpochSecond() + 600;
    }

    @Test
    void verifiesAValidToken() throws Exception {
        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        long exp = future();
        Optional<WebviewSignedToken.VerifiedToken> result =
                WebviewSignedToken.verify(forgeToken(uuid, exp));

        assertTrue(result.isPresent(), "valid token should verify");
        assertEquals(uuid, result.get().playerId());
        assertEquals(exp, result.get().expiresAtEpochSeconds());
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        String token = forgeToken(UUID.randomUUID(), future());
        // Tamper the first signature char, not the last: base64's final char has
        // padding bits the decoder ignores, so a flip there can be a no-op.
        int sigStart = token.indexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(sigStart + 1);
        assertTrue(WebviewSignedToken.verify(tampered).isEmpty());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = forgeToken(UUID.randomUUID(), Instant.now().getEpochSecond() - 5);
        assertTrue(WebviewSignedToken.verify(token).isEmpty());
    }

    @Test
    void rejectsNullBlankAndMalformed() {
        assertTrue(WebviewSignedToken.verify(null).isEmpty());
        assertTrue(WebviewSignedToken.verify("").isEmpty());
        assertTrue(WebviewSignedToken.verify("   ").isEmpty());
        assertTrue(WebviewSignedToken.verify("no-dot-here").isEmpty());
        assertTrue(WebviewSignedToken.verify(".").isEmpty());
        assertTrue(WebviewSignedToken.verify("!!!.@@@").isEmpty());
    }

    @Test
    void rejectsWrongVersionPrefix() throws Exception {
        // Payload "2|..." — right signature, unsupported version tag.
        String payload = "2|" + UUID.randomUUID() + "|" + future();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String token = enc.encodeToString(payloadBytes) + "." + enc.encodeToString(mac.doFinal(payloadBytes));
        assertTrue(WebviewSignedToken.verify(token).isEmpty());
    }

    @Test
    void rejectsTokenWhenNoSecretConfigured() throws Exception {
        String token = forgeToken(UUID.randomUUID(), future());
        setConfigSecret(""); // tokens effectively disabled
        assertTrue(WebviewSignedToken.verify(token).isEmpty());
    }
}
