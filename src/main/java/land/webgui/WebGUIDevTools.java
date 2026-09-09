package land.webgui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.keksuccino.rinku.RinkuBrowser;
import org.cef.browser.CefDevToolsClient;

/**
 * Chromium's own view of a page, in the game log.
 *
 * A page inside the game has no address bar and no inspector: the full DevTools window
 * needs {@code --remote-debugging-port}, which the browser library does not set and gives
 * no way to add, and {@code openDevTools()} needs a desktop window a game does not have.
 * The DevTools *protocol* needs neither, and it is where the browser keeps everything a
 * developer is missing — failed requests with their status, uncaught exceptions with a
 * stack, console calls with the line that made them.
 *
 * Attached per browser and only while the player has asked for it, because it is a
 * developer's tool and a chatty page would otherwise bury the log.
 */
public final class WebGUIDevTools {

    /** A page in a loop could produce thousands of these a second. */
    private static final int MAX_PER_SECOND = 20;
    /** Browsers already being watched, so a navigation does not attach a second time. */
    private static final java.util.Map<RinkuBrowser, Boolean> ATTACHED = new java.util.WeakHashMap<>();

    private static long windowStartedAt;
    private static int inWindow;
    private static boolean announcedSuppression;

    private WebGUIDevTools() {}

    /**
     * Turns the protocol on for one browser, once.
     *
     * Called on every page load, because that is the earliest point where the browser
     * certainly exists natively — but a browser navigates many times, and asking twice
     * would double every line in the log.
     *
     * Safe to call always: with the setting off it does nothing at all, so no page pays
     * for a feature nobody asked for.
     */
    public static void attachOnce(RinkuBrowser browser) {
        if (browser == null || !WebGUIClientConfig.devTools()) {
            return;
        }
        synchronized (ATTACHED) {
            // Identity, and weak: a browser is closed and forgotten without telling us.
            if (ATTACHED.containsKey(browser)) {
                return;
            }
            ATTACHED.put(browser, Boolean.TRUE);
        }
        CefDevToolsClient client;
        try {
            client = browser.getDevToolsClient();
        } catch (Throwable t) {
            WebGUIMod.LOGGER.warn("webgui devtools: no protocol client for this browser: {}", t.toString());
            return;
        }
        if (client == null) {
            return;
        }
        client.addEventListener(WebGUIDevTools::onEvent);
        // Each domain has to be asked for before it says anything. Network is the one
        // that answers "why is my page blank"; the other two carry errors and console.
        for (String domain : new String[] {"Log.enable", "Runtime.enable", "Network.enable"}) {
            client.executeDevToolsMethod(domain);
        }
        WebGUIMod.LOGGER.info("webgui devtools: watching {}", browser.getURL());
    }

    private static void onEvent(String method, String paramsJson) {
        if (!allow()) {
            return;
        }
        try {
            JsonObject params = paramsJson == null || paramsJson.isBlank()
                    ? new JsonObject()
                    : JsonParser.parseString(paramsJson).getAsJsonObject();
            switch (method) {
                case "Runtime.consoleAPICalled" -> consoleCall(params);
                case "Runtime.exceptionThrown" -> exception(params);
                case "Log.entryAdded" -> logEntry(params);
                case "Network.loadingFailed" -> loadingFailed(params);
                case "Network.responseReceived" -> responseReceived(params);
                default -> { /* The domains above are chatty enough. */ }
            }
        } catch (RuntimeException e) {
            // The payload shape is Chromium's, not ours, and a surprise in it must not
            // take down the callback that carries every other message.
            WebGUIMod.LOGGER.debug("webgui devtools: could not read {}: {}", method, e.toString());
        }
    }

    // --- the events worth a line ---------------------------------------------

    private static void consoleCall(JsonObject params) {
        StringBuilder text = new StringBuilder();
        for (JsonElement arg : array(params, "args")) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(describe(arg));
        }
        String where = firstFrame(params.getAsJsonObject("stackTrace"));
        String type = string(params, "type", "log");
        String line = "webgui devtools: console." + type + ": " + text + where;
        if (type.equals("error") || type.equals("assert")) {
            WebGUIMod.LOGGER.error(line);
        } else if (type.equals("warning")) {
            WebGUIMod.LOGGER.warn(line);
        } else {
            WebGUIMod.LOGGER.info(line);
        }
    }

    private static void exception(JsonObject params) {
        JsonObject details = params.getAsJsonObject("exceptionDetails");
        if (details == null) {
            return;
        }
        String text = string(details, "text", "uncaught exception");
        JsonObject thrown = details.getAsJsonObject("exception");
        if (thrown != null) {
            String described = string(thrown, "description", string(thrown, "value", ""));
            if (!described.isEmpty()) {
                text = described;
            }
        }
        WebGUIMod.LOGGER.error("webgui devtools: uncaught {}{}", text, firstFrame(details.getAsJsonObject("stackTrace")));
    }

    private static void logEntry(JsonObject params) {
        JsonObject entry = params.getAsJsonObject("entry");
        if (entry == null) {
            return;
        }
        String level = string(entry, "level", "info");
        String source = string(entry, "source", "");
        String url = string(entry, "url", "");
        String text = string(entry, "text", "");
        String line = "webgui devtools: [" + source + "] " + text + (url.isEmpty() ? "" : "  " + url);
        if (level.equals("error")) {
            WebGUIMod.LOGGER.error(line);
        } else if (level.equals("warning")) {
            WebGUIMod.LOGGER.warn(line);
        } else {
            WebGUIMod.LOGGER.info(line);
        }
    }

    private static void loadingFailed(JsonObject params) {
        // Cancelled requests are ordinary — a page navigating away does it — so only a
        // real error is worth a line.
        if (params.has("canceled") && params.get("canceled").getAsBoolean()) {
            return;
        }
        WebGUIMod.LOGGER.warn("webgui devtools: request failed ({}) {}",
                string(params, "type", "?"), string(params, "errorText", "unknown error"));
    }

    private static void responseReceived(JsonObject params) {
        JsonObject response = params.getAsJsonObject("response");
        if (response == null || !response.has("status")) {
            return;
        }
        int status = response.get("status").getAsInt();
        if (status < 400) {
            return;
        }
        WebGUIMod.LOGGER.warn("webgui devtools: {} {} ({})",
                status, string(response, "url", "?"), string(params, "type", "?"));
    }

    // --- reading Chromium's json --------------------------------------------

    /** A console argument as a person would want to read it. */
    private static String describe(JsonElement arg) {
        if (arg == null || !arg.isJsonObject()) {
            return String.valueOf(arg);
        }
        JsonObject o = arg.getAsJsonObject();
        if (o.has("value")) {
            JsonElement value = o.get("value");
            return value.isJsonPrimitive() ? value.getAsString() : value.toString();
        }
        // An object or a DOM node: Chromium sends a rendered form for exactly this.
        String described = string(o, "description", "");
        if (!described.isEmpty()) {
            return described;
        }
        return string(o, "className", string(o, "type", "?"));
    }

    /** Where it happened, as {@code  at file.js:12}, or nothing when unknown. */
    private static String firstFrame(JsonObject stackTrace) {
        if (stackTrace == null) {
            return "";
        }
        for (JsonElement element : array(stackTrace, "callFrames")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject frame = element.getAsJsonObject();
            String url = string(frame, "url", "");
            if (url.isEmpty()) {
                continue;
            }
            String function = string(frame, "functionName", "");
            int line = frame.has("lineNumber") ? frame.get("lineNumber").getAsInt() + 1 : 0;
            return "  at " + (function.isEmpty() ? "" : function + " ") + shorten(url) + ":" + line;
        }
        return "";
    }

    /**
     * Drops the origin and session token from a served URL.
     *
     * The token is 32 characters of noise in front of every path, and it turns the one
     * useful part of the line — which file — into something you have to hunt for.
     */
    private static String shorten(String url) {
        String base = WebGUIAssetServer.base();
        if (!base.isEmpty() && url.startsWith(base + "/")) {
            return url.substring(base.length() + 1);
        }
        return url;
    }

    private static JsonArray array(JsonObject o, String name) {
        JsonElement value = o == null ? null : o.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject o, String name, String fallback) {
        JsonElement value = o == null ? null : o.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static synchronized boolean allow() {
        long second = System.nanoTime() / 1_000_000_000L;
        if (second != windowStartedAt) {
            windowStartedAt = second;
            inWindow = 0;
            announcedSuppression = false;
        }
        if (++inWindow <= MAX_PER_SECOND) {
            return true;
        }
        if (!announcedSuppression) {
            announcedSuppression = true;
            WebGUIMod.LOGGER.warn("webgui devtools: more than {} messages in a second - dropping the rest of them",
                    MAX_PER_SECOND);
        }
        return false;
    }
}
