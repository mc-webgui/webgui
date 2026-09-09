package land.webgui;

import de.keksuccino.rinku.RinkuBrowser;
import com.google.gson.Gson;

public final class WebviewClientEmit {
    private static final Gson GSON = new Gson();

    private WebviewClientEmit() {}

    /** Called on the Minecraft client thread after receiving a WebviewEmitS2CPayload. */
    public static void dispatch(String eventName, String jsonPayload) {
        RinkuBrowser main = WebSession.browser();
        RinkuBrowser hud  = WebSession.hudBrowser();
        if (main == null && hud == null) return;

        // GSON.toJson produces a properly escaped JS string literal, e.g. "\"my\\\"event\""
        String encodedName = GSON.toJson(eventName);
        String data = (jsonPayload == null || jsonPayload.isBlank()) ? "null" : jsonPayload;

        String js = "(function(){"
                + "var n=" + encodedName + ";"
                + "var d=" + data + ";"
                + "window.dispatchEvent(new CustomEvent('webgui:'+n,{detail:d}));"
                + "if(window.webgui&&window.webgui._hs&&window.webgui._hs[n])"
                + "{window.webgui._hs[n].forEach(function(e){try{e.w({detail:d});}catch(x){}});}"
                + "})();";

        if (main != null) executeJs(main, js);
        if (hud != null && hud != main) executeJs(hud, js);
    }

    /**
     * Emits the death payload and leaves it on {@code window.webgui.death}.
     *
     * The event fires once, right after the document loads, so a bundle that only starts
     * subscribing from a component would miss it entirely. The snapshot gives such code
     * something to read, exactly as {@code window.webgui.client} and {@code .entity} do.
     */
    public static void dispatchDeath(String jsonPayload) {
        RinkuBrowser main = WebSession.browser();
        if (main == null) return;
        String data = (jsonPayload == null || jsonPayload.isBlank()) ? "null" : jsonPayload;
        String js = "(function(){"
                + "var d=" + data + ";"
                + "if(typeof window.webgui==='undefined')window.webgui={};"
                + "window.webgui.death=d;"
                + "window.dispatchEvent(new CustomEvent('webgui:death',{detail:d}));"
                + "if(window.webgui._hs&&window.webgui._hs['death'])"
                + "{window.webgui._hs['death'].forEach(function(e){try{e.w({detail:d});}catch(x){}});}"
                + "})();";
        executeJs(main, js);
    }

    private static void executeJs(RinkuBrowser browser, String js) {
        try {
            String url = browser.getURL();
            browser.executeJavaScript(js, url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui emit dispatch: {}", t.toString());
        }
    }
}
