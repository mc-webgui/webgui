package land.webgui;

import land.webgui.server.WebviewServerConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class WebviewJoinHud {

    private WebviewJoinHud() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    ServerPlayerEntity player = handler.player;
                    if (player == null) {
                        return;
                    }

                    String mainMenuUrl = WebviewServerConfig.mainMenuUrl();
                    if (!mainMenuUrl.isEmpty()) {
                        WebviewNetworking.sendMainMenuUrl(player, mainMenuUrl);
                    }

                    if (!WebviewServerConfig.autoHudOnJoin()) {
                        return;
                    }
                    String url = WebviewServerConfig.autoHudUrl();
                    if (url.isEmpty()) {
                        WebGUIMod.LOGGER.warn(
                                "webgui: autoHudOnJoin is true but autoHudUrl is empty — set autoHudUrl in config/webgui/server.json");
                        return;
                    }
                    WebviewNetworking.openHud(player, url);
                    WebGUIMod.LOGGER.info("webgui: auto HUD for {} → {}", player.getName().getString(), url);
                });
    }
}
