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

                    // First: everything below is pointless for a client that cannot receive it,
                    // and this is also where such a client is told so.
                    if (!land.webgui.server.WebviewClientCheck.onJoin(player)) {
                        return;
                    }

                    WebviewNetworking.sendConfigSnapshot(player);

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

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                land.webgui.server.WebviewRateLimiter.forget(handler.player.getUuid());
            }
        });
    }
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebviewJoinHud::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                land.webgui.server.WebviewRateLimiter.forget(event.getEntity().getUUID()));
    }

    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();

        // First: everything below is pointless for a client that cannot receive it, and
        // this is also where such a client is told so.
        if (!land.webgui.server.WebviewClientCheck.onJoin(player)) {
            return;
        }

        WebviewNetworking.sendConfigSnapshot(player);

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
