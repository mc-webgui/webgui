package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
//? } else {
/*import net.minecraft.client.Minecraft;
//? if >=1.21.5 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//? } else {
import net.neoforged.neoforge.network.PacketDistributor;
//? }*/
//? }
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

// Built-in channels: "close" — closes active GUI/HUD; "log" — logs to console. Others logged at INFO.
public final class WebviewPageToClientBridge {
    private WebviewPageToClientBridge() {}

    public static void register() {
        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId,
                                   String request, boolean persistent, CefQueryCallback callback) {
                try {
                    dispatch(request, callback);
                } catch (Throwable t) {
                    WebGUIMod.LOGGER.warn("[webgui page→game] handler error", t);
                    callback.failure(-1, t.getMessage() != null ? t.getMessage() : "error");
                }
                return true;
            }
        }, true);
        MCEF.getClient().getHandle().addMessageRouter(router);
    }

    private static void dispatch(String request, CefQueryCallback callback) {
        if (request == null || request.isBlank()) {
            callback.failure(-2, "empty request");
            return;
        }

        JsonObject obj = tryParseObject(request);

        if (obj == null) {
            WebGUIMod.LOGGER.info("[webgui page→game] {}", request);
            callback.success("{\"ok\":true}");
            return;
        }

        String channel = obj.has("channel") && !obj.get("channel").isJsonNull()
                ? obj.get("channel").getAsString()
                : "message";

        switch (channel) {
            case "log" -> {
                String level = obj.has("level") ? obj.get("level").getAsString() : "info";
                String msg   = obj.has("message") ? obj.get("message").getAsString() : request;
                log(level, msg);
            }
            case "close" -> {
                //? if fabric {
                MinecraftClient mc = MinecraftClient.getInstance();
                //? } else {
                /*Minecraft mc = Minecraft.getInstance();*/
                //? }
                mc.execute(() -> {
                    //? if fabric {
                    if (mc.currentScreen instanceof WebViewScreen) {
                        mc.currentScreen.close();
                    //? } else {
                    /*if (mc.screen instanceof WebViewScreen) {
                        mc.screen.onClose();*/
                    //? }
                    //? if fabric {
                    } else if (WebHudOverlay.isHudVisible()) {
                    //? } else {
                    /*} else if (WebHudOverlay.isHudVisible()) {*/
                    //? }
                        WebHudOverlay.toggleHud(mc);
                    }
                });
            }
            default -> {
                if (request.length() > WebviewPayloads.MAX_EVENT_DATA_LENGTH) {
                    WebGUIMod.LOGGER.warn("[webgui page→game] [{}] payload too large ({} bytes), dropping", channel, request.length());
                    break;
                }
                //? if fabric {
                MinecraftClient mc = MinecraftClient.getInstance();
                //? } else {
                /*Minecraft mc = Minecraft.getInstance();*/
                //? }
                //? if fabric {
                if (mc.getNetworkHandler() != null) {
                //? } else {
                /*if (mc.getConnection() != null) {*/
                //? }
                    String ch  = channel;
                    String pay = request;
                    mc.execute(() -> sendToServer(ch, pay));
                } else {
                    WebGUIMod.LOGGER.info("[webgui page→game] [{}] {}", channel, request);
                }
            }
        }

        callback.success("{\"ok\":true}");
    }

    private static JsonObject tryParseObject(String text) {
        try {
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static void sendToServer(String channel, String jsonPayload) {
        //? if fabric {
        //? if >=1.20.5 {
        if (ClientPlayNetworking.canSend(WebviewPayloads.WebviewPageEventC2SPayload.ID)) {
            ClientPlayNetworking.send(new WebviewPayloads.WebviewPageEventC2SPayload(channel, jsonPayload));
        }
        //? } else {
        /*net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeString(channel, WebviewPayloads.MAX_EVENT_NAME_LENGTH);
        buf.writeString(jsonPayload, WebviewPayloads.MAX_EVENT_DATA_LENGTH);
        ClientPlayNetworking.send(WebviewPayloads.PAGE_EVENT_CHANNEL, buf);*/
        //? }
        //? } else {
        /*//? if >=1.21.5 {
        ClientPacketDistributor.sendToServer(new WebviewPayloads.WebviewPageEventC2SPayload(channel, jsonPayload));
        //? } else {
        PacketDistributor.sendToServer(new WebviewPayloads.WebviewPageEventC2SPayload(channel, jsonPayload));
        //? }*/
        //? }
    }

    private static void log(String level, String msg) {
        String line = "[webgui page→game] " + msg;
        switch (level.toLowerCase()) {
            case "warn", "warning" -> WebGUIMod.LOGGER.warn(line);
            case "error"           -> WebGUIMod.LOGGER.error(line);
            case "debug"           -> WebGUIMod.LOGGER.debug(line);
            default                -> WebGUIMod.LOGGER.info(line);
        }
    }
}
