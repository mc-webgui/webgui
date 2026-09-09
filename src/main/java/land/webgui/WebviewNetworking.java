package land.webgui;

import land.webgui.server.WebviewServerConfig;
import land.webgui.server.WebviewServerEvents;
import land.webgui.server.WebviewSignedToken;
import land.webgui.server.WebviewUrlBuilder;
//? if fabric {
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//? } else {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;*/
//? }
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;*/
//? }

public final class WebviewNetworking {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MODE_GUI = 0;
    public static final int MODE_HUD = 1;
    public static final int MAX_URL_LENGTH = 16384;

    /** What the far end of a connection turned out to be. */
    public enum ClientKind {
        /** Speaks this protocol: the handshake channel is registered. */
        CURRENT,
        /** Has WebGUI, but an older build without the handshake channel. */
        OUTDATED,
        /** No WebGUI at all — a vanilla client, or one that simply does not have it. */
        ABSENT,
    }

    private WebviewNetworking() {}

    //? if fabric {
    public static void registerPayloadTypes() {
        //? if >=1.20.5 {
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.OpenWebS2CPayload.ID, WebviewPayloads.OpenWebS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebUIMainMenuPayload.ID, WebviewPayloads.WebUIMainMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewEmitS2CPayload.ID, WebviewPayloads.WebviewEmitS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewEntityContextS2CPayload.ID, WebviewPayloads.WebviewEntityContextS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewTrustedOriginsS2CPayload.ID, WebviewPayloads.WebviewTrustedOriginsS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewDeathS2CPayload.ID, WebviewPayloads.WebviewDeathS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewHelloS2CPayload.ID, WebviewPayloads.WebviewHelloS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WebviewPayloads.WebviewPageEventC2SPayload.ID, WebviewPayloads.WebviewPageEventC2SPayload.CODEC);
        //? }
    }
    //? } else {
    /*public static void registerPayloadTypes(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            // Optional, or NeoForge refuses any client that does not register every one
            // of these channels — including a client running an older WebGUI — and the
            // player is told "Incompatible client! Please use NeoForge <x>", which names
            // the wrong mod entirely. WebGUI enhances a client; it does not gate entry.
            // Servers that really do require it set requireClientMod in server.json, and
            // then the kick message says so in as many words.
            final var reg = event.registrar("1").optional();
            reg.playToServer(WebviewPayloads.WebviewPageEventC2SPayload.TYPE,
                    WebviewPayloads.WebviewPageEventC2SPayload.STREAM_CODEC,
                    (payload, ctx) -> {
                        net.minecraft.server.level.ServerPlayer sender = (net.minecraft.server.level.ServerPlayer) ctx.player();
                        // Checked here, off the server thread: enqueueing first would put the
                        // flood on the tick loop, which is the thing being protected.
                        if (sender == null || !land.webgui.server.WebviewRateLimiter.allow(sender.getUUID(), sender.getName().getString())) {
                            return;
                        }
                        ctx.enqueueWork(() ->
                                WebviewServerEvents.firePageEvent(sender, payload.channel(), payload.jsonPayload()));
                    });

            // S2C types register on both sides (the server sends them), but their
            // handlers touch client-only classes — loading those on a dedicated
            // server trips the RuntimeDistCleaner. So: real handlers on the client,
            // no-op handlers on the server (S2C is never received server-side).
            //? if >=1.21.5 {
            boolean client = net.neoforged.fml.loading.FMLEnvironment.getDist().isClient();
            //? } else {
            boolean client = net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
            //? }
            if (client) {
                WebGUIClient.registerClientReceivers(event);
            } else {
                reg.playToClient(WebviewPayloads.OpenWebS2CPayload.TYPE,
                        WebviewPayloads.OpenWebS2CPayload.STREAM_CODEC, (payload, ctx) -> {});
                reg.playToClient(WebviewPayloads.WebUIMainMenuPayload.TYPE,
                        WebviewPayloads.WebUIMainMenuPayload.STREAM_CODEC, (payload, ctx) -> {});
                reg.playToClient(WebviewPayloads.WebviewEmitS2CPayload.TYPE,
                        WebviewPayloads.WebviewEmitS2CPayload.STREAM_CODEC, (payload, ctx) -> {});
                reg.playToClient(WebviewPayloads.WebviewEntityContextS2CPayload.TYPE,
                        WebviewPayloads.WebviewEntityContextS2CPayload.STREAM_CODEC, (payload, ctx) -> {});
                reg.playToClient(WebviewPayloads.WebviewTrustedOriginsS2CPayload.TYPE,
                        WebviewPayloads.WebviewTrustedOriginsS2CPayload.STREAM_CODEC, (payload, ctx) -> {});
                reg.playToClient(WebviewPayloads.WebviewDeathS2CPayload.TYPE,
                        WebviewPayloads.WebviewDeathS2CPayload.STREAM_CODEC, (payload, ctx) -> {});
                reg.playToClient(WebviewPayloads.WebviewHelloS2CPayload.TYPE,
                        WebviewPayloads.WebviewHelloS2CPayload.STREAM_CODEC, (payload, ctx) -> {});
            }
        });
    }*/
    //? }

    //? if fabric {
    public static void registerServerReceivers() {
        //? if >=1.20.5 {
        ServerPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebviewPageEventC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String channel = payload.channel();
            String json    = payload.jsonPayload();
            // Checked here, off the server thread: scheduling first would put the flood on
            // the tick loop, which is the thing being protected.
            if (player == null || !land.webgui.server.WebviewRateLimiter.allow(player.getUuid(), player.getName().getString())) {
                return;
            }
            context.server().execute(() ->
                    WebviewServerEvents.PAGE_EVENT.invoker().onPageEvent(player, channel, json));
        });
        //? } else {
        /*ServerPlayNetworking.registerGlobalReceiver(WebviewPayloads.PAGE_EVENT_CHANNEL, (server, player, handler, buf, responseSender) -> {
            String channel = buf.readString(WebviewPayloads.MAX_EVENT_NAME_LENGTH);
            String json    = buf.readString(WebviewPayloads.MAX_EVENT_DATA_LENGTH);
            // Checked here, off the server thread: scheduling first would put the flood on
            // the tick loop, which is the thing being protected.
            if (player == null || !land.webgui.server.WebviewRateLimiter.allow(player.getUuid(), player.getName().getString())) {
                return;
            }
            server.execute(() -> WebviewServerEvents.PAGE_EVENT.invoker().onPageEvent(player, channel, json));
        });*/
        //? }
    }
    //? } else {
    /*public static void registerServerReceivers() {} // no-op: handled in registerPayloadTypes for NeoForge*/
    //? }

    //? if fabric {
    public static void openGui(ServerPlayerEntity player, String url) {
        clearEntityContext(player);
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL_VERSION);
        buf.writeVarInt(MODE_GUI);
        buf.writeString(withPlayerToken(player, url), MAX_URL_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.OPEN_WEB_CHANNEL, buf);*/
        //? }
    }

    public static void openHud(ServerPlayerEntity player, String url) {
        clearEntityContext(player);
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_HUD, withPlayerToken(player, url)));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL_VERSION);
        buf.writeVarInt(MODE_HUD);
        buf.writeString(withPlayerToken(player, url), MAX_URL_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.OPEN_WEB_CHANNEL, buf);*/
        //? }
    }

    public static void openGuiForEntity(ServerPlayerEntity player, String url, String entityJson) {
        sendEntityContext(player, entityJson);
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL_VERSION);
        buf.writeVarInt(MODE_GUI);
        buf.writeString(withPlayerToken(player, url), MAX_URL_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.OPEN_WEB_CHANNEL, buf);*/
        //? }
    }

    public static void sendEntityContext(ServerPlayerEntity player, String entityJson) {
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.WebviewEntityContextS2CPayload(entityJson));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(entityJson, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.ENTITY_CONTEXT_CHANNEL, buf);*/
        //? }
    }

    public static void clearEntityContext(ServerPlayerEntity player) {
        sendEntityContext(player, "null");
    }

    public static void emitToPage(ServerPlayerEntity player, String eventName, String jsonPayload) {
        String name = sanitizeStr(eventName, WebviewPayloads.MAX_EVENT_NAME_LENGTH);
        String data = (jsonPayload == null || jsonPayload.isBlank()) ? "null" : sanitizeStr(jsonPayload, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.WebviewEmitS2CPayload(name, data));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name, WebviewPayloads.MAX_EVENT_NAME_LENGTH);
        buf.writeString(data, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.EMIT_TO_PAGE_CHANNEL, buf);*/
        //? }
    }

    public static void sendMainMenuUrl(ServerPlayerEntity player, String url) {
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.WebUIMainMenuPayload(sanitizeUrl(url)));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sanitizeUrl(url), MAX_URL_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.MAIN_MENU_CHANNEL, buf);*/
        //? }
    }

    /**
     * Tells the client which page replaces the vanilla death screen, and — when the
     * player has just died — what killed them.
     *
     * The URL carries a per-player token like any other page the mod opens, so the
     * death page can verify who it is talking about.
     */
    public static void sendDeathScreen(ServerPlayerEntity player, String url, String infoJson) {
        String u = (url == null || url.isBlank()) ? "" : withPlayerToken(player, url);
        String info = (infoJson == null || infoJson.isBlank()) ? "" : sanitizeStr(infoJson, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.WebviewDeathS2CPayload(u, info));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(u, MAX_URL_LENGTH);
        buf.writeString(info, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.DEATH_SCREEN_CHANNEL, buf);*/
        //? }
    }

    /**
     * Tells the client which WebGUI the server runs. Silently skipped for a client that
     * has no such channel — that case is reported to the player as text instead.
     */
    public static void sendHello(ServerPlayerEntity player) {
        //? if >=1.20.5 {
        if (!ServerPlayNetworking.canSend(player, WebviewPayloads.WebviewHelloS2CPayload.ID)) {
            return;
        }
        ServerPlayNetworking.send(player, new WebviewPayloads.WebviewHelloS2CPayload(PROTOCOL_VERSION, WebGUIVersion.current()));
        //? } else {
        /*if (!ServerPlayNetworking.canSend(player, WebviewPayloads.HELLO_CHANNEL)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL_VERSION);
        buf.writeString(WebGUIVersion.current(), WebviewPayloads.MAX_VERSION_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.HELLO_CHANNEL, buf);*/
        //? }
    }

    /** Whether the player's client has WebGUI, and whether it is new enough to talk to us. */
    public static ClientKind clientKind(ServerPlayerEntity player) {
        //? if >=1.20.5 {
        if (ServerPlayNetworking.canSend(player, WebviewPayloads.WebviewHelloS2CPayload.ID)) {
            return ClientKind.CURRENT;
        }
        // No handshake channel but the original one is there: WebGUI, just an older build.
        return ServerPlayNetworking.canSend(player, WebviewPayloads.OpenWebS2CPayload.ID)
                ? ClientKind.OUTDATED
                : ClientKind.ABSENT;
        //? } else {
        /*if (ServerPlayNetworking.canSend(player, WebviewPayloads.HELLO_CHANNEL)) {
            return ClientKind.CURRENT;
        }
        return ServerPlayNetworking.canSend(player, WebviewPayloads.OPEN_WEB_CHANNEL)
                ? ClientKind.OUTDATED
                : ClientKind.ABSENT;*/
        //? }
    }

    public static void sendTrustedOrigins(ServerPlayerEntity player, String origins) {
        String o = origins == null ? "" : origins;
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, new WebviewPayloads.WebviewTrustedOriginsS2CPayload(o));
        //? } else {
        /*PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(o, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        ServerPlayNetworking.send(player, WebviewPayloads.TRUSTED_ORIGINS_CHANNEL, buf);*/
        //? }
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
    //? } else {
    /*public static void openGui(ServerPlayer player, String url) {
        clearEntityContext(player);
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
    }

    public static void openHud(ServerPlayer player, String url) {
        clearEntityContext(player);
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_HUD, withPlayerToken(player, url)));
    }

    public static void openGuiForEntity(ServerPlayer player, String url, String entityJson) {
        sendEntityContext(player, entityJson);
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.OpenWebS2CPayload(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url)));
    }

    public static void sendEntityContext(ServerPlayer player, String entityJson) {
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewEntityContextS2CPayload(entityJson));
    }

    public static void clearEntityContext(ServerPlayer player) {
        sendEntityContext(player, "null");
    }

    public static void emitToPage(ServerPlayer player, String eventName, String jsonPayload) {
        String name = sanitizeStr(eventName, WebviewPayloads.MAX_EVENT_NAME_LENGTH);
        String data = (jsonPayload == null || jsonPayload.isBlank()) ? "null" : sanitizeStr(jsonPayload, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewEmitS2CPayload(name, data));
    }

    public static void sendMainMenuUrl(ServerPlayer player, String url) {
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebUIMainMenuPayload(sanitizeUrl(url)));
    }

    public static void sendDeathScreen(ServerPlayer player, String url, String infoJson) {
        String u = (url == null || url.isBlank()) ? "" : withPlayerToken(player, url);
        String info = (infoJson == null || infoJson.isBlank()) ? "" : sanitizeStr(infoJson, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewDeathS2CPayload(u, info));
    }

    public static void sendTrustedOrigins(ServerPlayer player, String origins) {
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewTrustedOriginsS2CPayload(origins == null ? "" : origins));
    }

    // Tells the client which WebGUI the server runs. Silently skipped for a client that
    // has no such channel — that case is reported to the player as text instead.
    public static void sendHello(ServerPlayer player) {
        if (!player.connection.hasChannel(WebviewPayloads.WebviewHelloS2CPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new WebviewPayloads.WebviewHelloS2CPayload(PROTOCOL_VERSION, WebGUIVersion.current()));
    }

    // Whether the player's client has WebGUI, and whether it is new enough to talk to us.
    public static ClientKind clientKind(ServerPlayer player) {
        if (player.connection.hasChannel(WebviewPayloads.WebviewHelloS2CPayload.TYPE)) {
            return ClientKind.CURRENT;
        }
        // No handshake channel but the original one is there: WebGUI, just an older build.
        return player.connection.hasChannel(WebviewPayloads.OpenWebS2CPayload.TYPE)
                ? ClientKind.OUTDATED
                : ClientKind.ABSENT;
    }

    private static String withPlayerToken(ServerPlayer player, String url) {
        if (!WebviewServerConfig.enableTokens()) {
            return sanitizeUrl(url);
        }
        String token = WebviewSignedToken.create(player);
        if (token.isEmpty()) {
            return sanitizeUrl(url);
        }
        String withParam = WebviewUrlBuilder.appendQueryParam(url == null ? "" : url, WebviewServerConfig.queryParamName(), token);
        return sanitizeUrl(withParam);
    }*/
    //? }

    private static String sanitizeUrl(String url) {
        if (url == null) {
            return "";
        }
        if (url.length() > MAX_URL_LENGTH) {
            return url.substring(0, MAX_URL_LENGTH);
        }
        return url;
    }

    private static String sanitizeStr(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
