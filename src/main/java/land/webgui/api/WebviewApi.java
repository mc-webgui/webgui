package land.webgui.api;

import land.webgui.WebviewNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Call from the server thread. */
public final class WebviewApi {
    private WebviewApi() {}

    /** If signed tokens are enabled in server.json, a token is appended to the URL automatically. */
    public static void openGui(ServerPlayerEntity player, String url) {
        WebviewNetworking.openGui(player, url);
    }

    /** If signed tokens are enabled in server.json, a token is appended to the URL automatically. */
    public static void openHud(ServerPlayerEntity player, String url) {
        WebviewNetworking.openHud(player, url);
    }

    /** Player opens this URL via the main menu keybind (default F6). */
    public static void sendMainMenuUrl(ServerPlayerEntity player, String url) {
        WebviewNetworking.sendMainMenuUrl(player, url);
    }

    /** Maximum URL length accepted by the mod (16 384 characters). */
    public static int maxUrlLength() {
        return WebviewNetworking.MAX_URL_LENGTH;
    }

    /** S2C protocol version this build of the mod uses. */
    public static int protocolVersion() {
        return WebviewNetworking.PROTOCOL_VERSION;
    }
}
