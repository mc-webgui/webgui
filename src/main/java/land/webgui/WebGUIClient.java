package land.webgui;

import de.keksuccino.rinku.Rinku;
//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? } else {
/*import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.DeathScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;*/
//? }

public final class WebGUIClient
        //? if fabric {
        implements ClientModInitializer
        //? }
{

    //? if fabric {
    @Override
    public void onInitializeClient() {
        Rinku.scheduleForInit(success -> {
            if (!success) {
                WebGUIMod.LOGGER.error("Rinku (Chromium) failed to initialize — web GUI will not work.");
                return;
            }
            Rinku.getClient().addDisplayHandler(new WebviewBrowserConsoleLogger());
            WebviewPageToClientBridge.register();
            WebviewPageLoadHooks.register();
            WebGUIMod.LOGGER.info("WebGUI bridge ready (console log, page↔game, client data).");
        });

        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.OpenWebS2CPayload.ID, (payload, context) -> {
            if (payload.protocolVersion() != WebviewNetworking.PROTOCOL_VERSION) {
                return;
            }
            context.client().execute(() -> handleOpenPayload(context.client(), payload.displayMode(), payload.url()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebUIMainMenuPayload.ID, (payload, context) -> {
            context.client().execute(() -> WebGUIMainMenuUrl.setUrl(payload.url()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewEmitS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> WebviewClientEmit.dispatch(payload.eventName(), payload.jsonPayload()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewEntityContextS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> WebviewClientBridge.setEntityContext(payload.entityJson()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewTrustedOriginsS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> WebGUITrustedOrigins.set(payload.origins()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewDeathS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> onDeathPayload(payload.url(), payload.infoJson()));
        });
        //? } else {
        /*ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.OPEN_WEB_CHANNEL, (client, handler, buf, responseSender) -> {
            int protocolVersion = buf.readVarInt();
            int displayMode = buf.readVarInt();
            String url = buf.readString(WebviewNetworking.MAX_URL_LENGTH);
            if (protocolVersion != WebviewNetworking.PROTOCOL_VERSION) {
                return;
            }
            client.execute(() -> handleOpenPayload(client, displayMode, url));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.MAIN_MENU_CHANNEL, (client, handler, buf, responseSender) -> {
            String url = buf.readString(WebviewNetworking.MAX_URL_LENGTH);
            client.execute(() -> WebGUIMainMenuUrl.setUrl(url));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.EMIT_TO_PAGE_CHANNEL, (client, handler, buf, responseSender) -> {
            String eventName   = buf.readString(WebviewPayloads.MAX_EVENT_NAME_LENGTH);
            String jsonPayload = buf.readString(WebviewPayloads.MAX_EVENT_DATA_LENGTH);
            client.execute(() -> WebviewClientEmit.dispatch(eventName, jsonPayload));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.TRUSTED_ORIGINS_CHANNEL, (client, handler, buf, responseSender) -> {
            String origins = buf.readString(WebviewPayloads.MAX_EVENT_DATA_LENGTH);
            client.execute(() -> WebGUITrustedOrigins.set(origins));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.DEATH_SCREEN_CHANNEL, (client, handler, buf, responseSender) -> {
            String url  = buf.readString(WebviewNetworking.MAX_URL_LENGTH);
            String info = buf.readString(WebviewPayloads.MAX_EVENT_DATA_LENGTH);
            client.execute(() -> onDeathPayload(url, info));
        });*/
        //? }

        WebGUIKeys.register();
        WebHudOverlay.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WebHudOverlay.tickCursor(client);
            WebGUIKeys.tick(client);
            WebviewClientBridge.tick(client);
        });

        // Leaving a world (disconnect / exit to title) tears down any server-opened HUD or GUI
        // browser so it doesn't keep rendering in the background on the main menu.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(WebGUIClient::onLeaveWorld));
    }

    private static void onLeaveWorld() {
        WebHudOverlay.reset();
        WebSession.dispose();
        WebGUIMainMenuUrl.setUrl("");
        WebGUITrustedOrigins.clear();
        WebGUIDeathScreen.clear();
    }

    /**
     * A url with no info is the join-time push; an info payload means the player
     * just died. Both arrive on the same channel because the client needs the url
     * in hand before the death, not alongside it.
     */
    static void onDeathPayload(String url, String infoJson) {
        WebGUIDeathScreen.setUrl(url);
        if (infoJson != null && !infoJson.isBlank()) {
            WebGUIDeathScreen.setInfo(infoJson);
        }
    }

    private static void handleOpenPayload(net.minecraft.client.MinecraftClient client, int mode, String url) {
        if (!Rinku.isInitialized()) {
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.translatable("message.webgui.mcef_not_ready"), false);
            }
            return;
        }
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (mode == WebviewNetworking.MODE_GUI) {
            client.setScreen(new WebViewScreen(u));
        } else if (mode == WebviewNetworking.MODE_HUD) {
            WebHudOverlay.applyServerOpen(client, u);
        }
    }
    //? } else {
    /*public static void initClient(IEventBus modBus) {
        Rinku.scheduleForInit(success -> {
            if (!success) {
                WebGUIMod.LOGGER.error("Rinku (Chromium) failed to initialize - web GUI will not work.");
                return;
            }
            Rinku.getClient().addDisplayHandler(new WebviewBrowserConsoleLogger());
            WebviewPageToClientBridge.register();
            WebviewPageLoadHooks.register();
            WebGUIMod.LOGGER.info("WebGUI bridge ready (console log, page<->game, client data).");
        });

        // Payload registration moved to common init (WebGUIMod) so the dedicated
        // server also registers the S2C channels — see issue #4.
        WebGUIKeys.register(modBus);
        WebHudOverlay.register();
        NeoForge.EVENT_BUS.addListener(WebGUIClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(WebGUIClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(WebGUIClient::onScreenOpening);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        onLeaveWorld();
    }

    // Leaving a world (disconnect / exit to title) tears down any server-opened HUD or GUI
    // browser so it doesn't keep rendering in the background on the main menu.
    private static void onLeaveWorld() {
        WebHudOverlay.reset();
        WebSession.dispose();
        WebGUIMainMenuUrl.setUrl("");
        WebGUITrustedOrigins.clear();
        WebGUIDeathScreen.clear();
    }

    // A url with no info is the join-time push; an info payload means the player
    // just died. Both share a channel because the client needs the url in hand
    // before the death rather than alongside it.
    static void onDeathPayload(String url, String infoJson) {
        WebGUIDeathScreen.setUrl(url);
        if (infoJson != null && !infoJson.isBlank()) {
            WebGUIDeathScreen.setInfo(infoJson);
        }
    }

    // Called only on the client (from WebviewNetworking.registerPayloadTypes) so
    // these client-only handlers never load on a dedicated server.
    public static void registerClientReceivers(RegisterPayloadHandlersEvent event) {
        final var reg = event.registrar("1");
        reg.playToClient(WebviewPayloads.OpenWebS2CPayload.TYPE, WebviewPayloads.OpenWebS2CPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (payload.protocolVersion() != WebviewNetworking.PROTOCOL_VERSION) return;
                    ctx.enqueueWork(() -> handleOpenPayload(Minecraft.getInstance(), payload.displayMode(), payload.url()));
                });
        reg.playToClient(WebviewPayloads.WebUIMainMenuPayload.TYPE, WebviewPayloads.WebUIMainMenuPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebGUIMainMenuUrl.setUrl(payload.url())));
        reg.playToClient(WebviewPayloads.WebviewEmitS2CPayload.TYPE, WebviewPayloads.WebviewEmitS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebviewClientEmit.dispatch(payload.eventName(), payload.jsonPayload())));
        reg.playToClient(WebviewPayloads.WebviewEntityContextS2CPayload.TYPE, WebviewPayloads.WebviewEntityContextS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebviewClientBridge.setEntityContext(payload.entityJson())));
        reg.playToClient(WebviewPayloads.WebviewTrustedOriginsS2CPayload.TYPE, WebviewPayloads.WebviewTrustedOriginsS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebGUITrustedOrigins.set(payload.origins())));
        reg.playToClient(WebviewPayloads.WebviewDeathS2CPayload.TYPE, WebviewPayloads.WebviewDeathS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> onDeathPayload(payload.url(), payload.infoJson())));
    }

    // Swaps the vanilla death screen for the server's page. Fabric needs a mixin
    // for this; NeoForge hands us the screen before it opens, which also spares
    // us Mojang mappings renaming setScreen to setScreenAndShow in 26.2.
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen) || !WebGUIDeathScreen.configured()) {
            return;
        }
        // No browser, no page. WebViewScreen closes itself when Chromium is not
        // ready, vanilla reopens the death screen because the player is still
        // dead, and we would replace it again — a loop that crashes the client.
        if (!Rinku.isInitialized()) {
            return;
        }
        WebGUIDeathScreen.setActive(true);
        event.setNewScreen(new WebViewScreen(WebGUIDeathScreen.url()));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        WebHudOverlay.tickCursor(mc);
        WebGUIKeys.tick(mc);
        WebviewClientBridge.tick(mc);
    }

    private static void handleOpenPayload(Minecraft client, int mode, String url) {
        if (!Rinku.isInitialized()) {
            if (client.player != null) {
                //? if >=26 {
                client.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("message.webgui.mcef_not_ready"));
                //? } else {
                client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.webgui.mcef_not_ready"), false);
                //? }
            }
            return;
        }
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (mode == WebviewNetworking.MODE_GUI) {
            //? if >=26.2-neoforge {
            client.gui.setScreen(new WebViewScreen(u));
            //? } else {
            client.setScreen(new WebViewScreen(u));
            //? }
        } else if (mode == WebviewNetworking.MODE_HUD) {
            WebHudOverlay.applyServerOpen(client, u);
        }
    }*/
    //? }
}
