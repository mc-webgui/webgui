package land.webgui;

import land.webgui.server.WebviewServerConfig;
import land.webgui.server.WebviewSignedToken;
import land.webgui.server.WebviewUrlBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class WebviewNetworking {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MODE_GUI = 0;
    public static final int MODE_HUD = 1;
    public static final int MAX_URL_LENGTH = 16384;

    private WebviewNetworking() {}

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.OpenWebS2CPayload.ID, WebviewPayloads.OpenWebS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebUIMainMenuPayload.ID, WebviewPayloads.WebUIMainMenuPayload.CODEC);
    }

    public static void openGui(ServerPlayerEntity player, String url) {
        ServerPlayNetworking.send(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
    }

    public static void openHud(ServerPlayerEntity player, String url) {
        ServerPlayNetworking.send(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_HUD, withPlayerToken(player, url)));
    }

    public static void sendMainMenuUrl(ServerPlayerEntity player, String url) {
        ServerPlayNetworking.send(player, new WebviewPayloads.WebUIMainMenuPayload(sanitizeUrl(url)));
    }

    private static String withPlayerToken(ServerPlayerEntity player, String url) {
        if (!WebviewServerConfig.enableTokens()) {
            return sanitizeUrl(url);
        }
        String token = WebviewSignedToken.create(player);
        if (token.isEmpty()) {
            return sanitizeUrl(url);
        }
        String withParam = WebviewUrlBuilder.appendQueryParam(url == null ? "" : url, WebviewServerConfig.queryParamName(), token);
        return sanitizeUrl(withParam);
    }

    private static String sanitizeUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.length() > MAX_URL_LENGTH) {
            return url.substring(0, MAX_URL_LENGTH);
        }
        return url;
    }
}
