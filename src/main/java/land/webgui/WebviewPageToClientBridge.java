package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                mc.execute(() -> {
                    if (mc.currentScreen instanceof WebViewScreen) {
                        mc.currentScreen.close();
                    } else if (WebHudOverlay.isHudVisible()) {
                        WebHudOverlay.toggleHud(mc);
                    }
                });
            }
            default -> WebGUIMod.LOGGER.info("[webgui page→game] [{}] {}", channel, request);
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
