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
            WebviewDownloadHandler.register();
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

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewHelloS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> WebGUIHandshake.onHello(payload.protocolVersion(), payload.modVersion()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewAssetManifestS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> onAssetManifest(payload.revision(), payload.manifest()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewAssetChunkS2CPayload.ID, (payload, context) -> {
            // Straight through, off the render thread: a page pulls dozens of files and
            // every one of them would otherwise queue behind a frame.
            WebGUIAssetCache.onChunk(payload.path(), payload.chunkIndex(), payload.chunkCount(), payload.bytes());
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
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.HELLO_CHANNEL, (client, handler, buf, responseSender) -> {
            int protocol   = buf.readVarInt();
            String version = buf.readString(WebviewPayloads.MAX_VERSION_LENGTH);
            client.execute(() -> WebGUIHandshake.onHello(protocol, version));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.ASSET_MANIFEST_CHANNEL, (client, handler, buf, responseSender) -> {
            String rev      = buf.readString(WebviewPayloads.MAX_VERSION_LENGTH);
            String manifest = buf.readString(WebviewPayloads.MAX_MANIFEST_LENGTH);
            client.execute(() -> onAssetManifest(rev, manifest));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.ASSET_CHUNK_CHANNEL, (client, handler, buf, responseSender) -> {
            String path = buf.readString(WebviewPayloads.MAX_ASSET_PATH_LENGTH);
            int index   = buf.readVarInt();
            int count   = buf.readVarInt();
            byte[] data = buf.readByteArray(WebviewPayloads.ASSET_CHUNK_BYTES);
            // Straight through, off the render thread: a page pulls dozens of files and
            // every one of them would otherwise queue behind a frame.
            WebGUIAssetCache.onChunk(path, index, count, data);
        });*/
        //? }

        WebGUIClientConfig.load();
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
        WebGUIHandshake.clear();
        WebGUIAssetCache.clear();
        WebGUIAssetServer.invalidateSession();
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
            WebviewDownloadHandler.register();
            WebGUIMod.LOGGER.info("WebGUI bridge ready (console log, page<->game, client data).");
        });

        // Payload registration moved to common init (WebGUIMod) so the dedicated
        // server also registers the S2C channels — see issue #4.
        WebGUIClientConfig.load();
        WebGUIKeys.register(modBus);
        WebHudOverlay.register();
        // Puts a Config button on the mod's entry in NeoForge's own mod list. Looked up
        // rather than taken from the constructor, so the mod's entry point keeps the
        // signature it has.
        net.neoforged.fml.ModList.get().getModContainerById(WebGUIMod.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                        (c, parent) -> new WebGUISettingsScreen(parent)));
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
        WebGUIHandshake.clear();
        WebGUIAssetCache.clear();
        WebGUIAssetServer.invalidateSession();
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
        // Optional for the same reason the server side is: a required channel here would
        // make this client refuse any server running an older WebGUI, and NeoForge would
        // blame its own version for it.
        final var reg = event.registrar("1").optional();
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
        reg.playToClient(WebviewPayloads.WebviewHelloS2CPayload.TYPE, WebviewPayloads.WebviewHelloS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> WebGUIHandshake.onHello(payload.protocolVersion(), payload.modVersion())));
        reg.playToClient(WebviewPayloads.WebviewAssetManifestS2CPayload.TYPE, WebviewPayloads.WebviewAssetManifestS2CPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> onAssetManifest(payload.revision(), payload.manifest())));
        reg.playToClient(WebviewPayloads.WebviewAssetChunkS2CPayload.TYPE, WebviewPayloads.WebviewAssetChunkS2CPayload.STREAM_CODEC,
                // Straight through, not enqueued: a page pulls dozens of files and every
                // one of them would otherwise queue behind a frame.
                (payload, ctx) -> WebGUIAssetCache.onChunk(payload.path(), payload.chunkIndex(), payload.chunkCount(), payload.bytes()));
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

    /**
     * Takes the list of pages a server ships and gets ready to serve them.
     *
     * The local server starts here rather than at launch: a client that never joins a
     * server using this feature has no reason to be listening on anything.
     */
    private static void onAssetManifest(String revision, String manifest) {
        WebGUIAssetCache.useCacheDir(assetCacheDir());
        // Kept from before the manifest is replaced: a second manifest in one session
        // means the server reloaded, and only a different revision means the files
        // actually moved. Empty is the first manifest of a session, where there is
        // nothing open yet to refresh.
        String previous = WebGUIAssetCache.revision();
        WebGUIAssetCache.setManifest(revision, manifest);
        if (WebGUIAssetCache.isEmpty()) {
            WebGUIAssetServer.invalidateSession();
            return;
        }
        WebGUIAssetServer.ensureStarted();
        // These pages come from the server the player is on, so refusing them the command
        // channel would leave the one place a server fully controls as the one place it
        // cannot use — and unlike a web host, nobody else can put a file there.
        WebGUITrustedOrigins.allowAlso(WebGUIAssetServer.origin());
        if (!previous.isEmpty() && !previous.equals(WebGUIAssetCache.revision())) {
            WebSession.reloadBundledPages();
        }
    }

    /**
     * Where downloaded pages are kept between sessions.
     *
     * Under the game directory rather than the config directory: this is a cache, it can
     * be deleted at any time, and nothing in it is meant to be edited by hand.
     */
    private static java.nio.file.Path assetCacheDir() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("webgui-cache");
        //? } else {
        /*return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("webgui-cache");*/
        //? }
    }

    /**
     * Asks the server for one file.
     *
     * Called from the local page server's thread, so the send is handed to the client
     * thread rather than done here: NeoForge's distributor reaches for the connection
     * belonging to the calling thread and comes up empty off it, which showed up as
     * every bundled page silently failing to load on NeoForge while Fabric was fine.
     */
    public static void requestAsset(String path) {
        //? if fabric {
        net.minecraft.client.MinecraftClient.getInstance().execute(() -> sendAssetRequest(path));
        //? } else {
        /*net.minecraft.client.Minecraft.getInstance().execute(() -> sendAssetRequest(path));*/
        //? }
    }

    private static void sendAssetRequest(String path) {
        //? if fabric {
        //? if >=1.20.5 {
        if (ClientPlayNetworking.canSend(WebviewPayloads.WebviewAssetRequestC2SPayload.ID)) {
            ClientPlayNetworking.send(new WebviewPayloads.WebviewAssetRequestC2SPayload(path));
        }
        //? } else {
        /*net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(path, WebviewPayloads.MAX_ASSET_PATH_LENGTH);
        ClientPlayNetworking.send(WebviewPayloads.ASSET_REQUEST_CHANNEL, buf);*/
        //? }
        //? } else {
        /*//? if >=1.21.5 {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new WebviewPayloads.WebviewAssetRequestC2SPayload(path));
        //? } else {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new WebviewPayloads.WebviewAssetRequestC2SPayload(path));
        //? }*/
        //? }
    }
}
