package land.webgui;

import land.webgui.server.WebviewServerConfig;
//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;*/
//? }

public final class WebviewJoinHud {

    private WebviewJoinHud() {}

    //? if fabric {
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
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebviewJoinHud::onPlayerJoin);
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();

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
        WebGUIMod.LOGGER.info("webgui: auto HUD for {} -> {}", player.getName().getString(), url);
    }*/
    //? }
}
