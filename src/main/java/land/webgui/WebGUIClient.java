package land.webgui;

import com.cinemamod.mcef.MCEF;
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
        MCEF.scheduleForInit(success -> {
            if (!success) {
                WebGUIMod.LOGGER.error("MCEF (Chromium) failed to initialize — web GUI will not work.");
                return;
            }
            MCEF.getClient().addDisplayHandler(new WebviewBrowserConsoleLogger());
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
    }

    private static void handleOpenPayload(net.minecraft.client.MinecraftClient client, int mode, String url) {
        if (!MCEF.isInitialized()) {
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
        MCEF.scheduleForInit(success -> {
            if (!success) {
                WebGUIMod.LOGGER.error("MCEF (Chromium) failed to initialize - web GUI will not work.");
                return;
            }
            MCEF.getClient().addDisplayHandler(new WebviewBrowserConsoleLogger());
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
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        WebHudOverlay.tickCursor(mc);
        WebGUIKeys.tick(mc);
        WebviewClientBridge.tick(mc);
    }

    private static void handleOpenPayload(Minecraft client, int mode, String url) {
        if (!MCEF.isInitialized()) {
            if (client.player != null) {
                client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.webgui.mcef_not_ready"), false);
            }
            return;
        }
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (mode == WebviewNetworking.MODE_GUI) {
            client.setScreen(new WebViewScreen(u));
        } else if (mode == WebviewNetworking.MODE_HUD) {
            WebHudOverlay.applyServerOpen(client, u);
        }
    }*/
    //? }
}
