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

    private WebviewNetworking() {}

    //? if fabric {
    public static void registerPayloadTypes() {
        //? if >=1.20.5 {
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.OpenWebS2CPayload.ID, WebviewPayloads.OpenWebS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebUIMainMenuPayload.ID, WebviewPayloads.WebUIMainMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewEmitS2CPayload.ID, WebviewPayloads.WebviewEmitS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WebviewPayloads.WebviewEntityContextS2CPayload.ID, WebviewPayloads.WebviewEntityContextS2CPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WebviewPayloads.WebviewPageEventC2SPayload.ID, WebviewPayloads.WebviewPageEventC2SPayload.CODEC);
        //? }
    }
    //? } else {
    /*public static void registerPayloadTypes(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            final var reg = event.registrar("1");
            reg.playToServer(WebviewPayloads.WebviewPageEventC2SPayload.TYPE,
                    WebviewPayloads.WebviewPageEventC2SPayload.STREAM_CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() ->
                            WebviewServerEvents.firePageEvent((net.minecraft.server.level.ServerPlayer) ctx.player(), payload.channel(), payload.jsonPayload())));
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
            context.server().execute(() ->
                    WebviewServerEvents.PAGE_EVENT.invoker().onPageEvent(player, channel, json));
        });
        //? } else {
        /*ServerPlayNetworking.registerGlobalReceiver(WebviewPayloads.PAGE_EVENT_CHANNEL, (server, player, handler, buf, responseSender) -> {
            String channel = buf.readString(WebviewPayloads.MAX_EVENT_NAME_LENGTH);
            String json    = buf.readString(WebviewPayloads.MAX_EVENT_DATA_LENGTH);
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
