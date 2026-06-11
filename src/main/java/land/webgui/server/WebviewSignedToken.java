package land.webgui.server;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

//? if fabric {
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.server.level.ServerPlayer;*/
//? }

public final class WebviewSignedToken {
    private static final String HMAC_SHA256 = "HmacSHA256";

    public record VerifiedToken(UUID playerId, long expiresAtEpochSeconds) {}

    private WebviewSignedToken() {}

    //? if fabric {
    public static String create(ServerPlayerEntity player) {
    //? } else {
    /*public static String create(ServerPlayer player) {*/
    //? }
        byte[] secret = WebviewServerConfig.tokenSecretBytes();
        if (secret.length == 0) {
            return "";
        }
        long exp = Instant.now().getEpochSecond() + WebviewServerConfig.tokenTtlSeconds();
        //? if fabric {
        String payload = "1|" + player.getUuid() + "|" + exp;
        //? } else {
        /*String payload = "1|" + player.getUUID() + "|" + exp;*/
        //? }
        byte[] sig = hmac(secret, payload.getBytes(StandardCharsets.UTF_8));
        var enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + enc.encodeToString(sig);
    }

    public static Optional<VerifiedToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        byte[] secret = WebviewServerConfig.tokenSecretBytes();
        if (secret.length == 0) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            return Optional.empty();
        }
        String encPayload = token.substring(0, dot);
        String encSig = token.substring(dot + 1);
        byte[] payloadBytes;
        byte[] sigBytes;
        try {
            var dec = Base64.getUrlDecoder();
            payloadBytes = dec.decode(encPayload);
            sigBytes = dec.decode(encSig);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        byte[] expectedSig = hmac(secret, payload.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expectedSig, sigBytes)) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3 || !"1".equals(parts[0])) {
            return Optional.empty();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        long exp;
        try {
            exp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() > exp) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedToken(uuid, exp));
    }

    private static byte[] hmac(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
