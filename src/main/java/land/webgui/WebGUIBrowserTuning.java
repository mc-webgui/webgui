package land.webgui;

import land.webgui.browser.WebBrowser;

/**
 * How often the browser is allowed to paint.
 *
 * A windowless browser has a frame rate of its own, and the browser library never sets it,
 * so the page repaints at whatever CEF defaults to. Sending data more often cannot help
 * past that ceiling: the page would hold fresh numbers and still be drawn at the old rate,
 * which reads as the overlay trailing the world.
 */
public final class WebGUIBrowserTuning {

    private static final java.util.Map<WebBrowser, Boolean> TUNED = new java.util.WeakHashMap<>();

    private WebGUIBrowserTuning() {}

    public static void applyOnce(WebBrowser browser) {
        if (browser == null) {
            return;
        }
        synchronized (TUNED) {
            if (TUNED.containsKey(browser)) {
                return;
            }
            TUNED.put(browser, Boolean.TRUE);
        }

        try {
            int target = WebGUIClientConfig.browserFrameRate();
            if (target > 0) {
                browser.setWindowlessFrameRate(target);
                WebGUIMod.LOGGER.info("webgui: browser paint rate asked for {} fps", target);
            }
        } catch (Throwable t) {
            WebGUIMod.LOGGER.warn("webgui: could not set the browser paint rate: {}", t.toString());
        }
    }

    static void forget() {
        synchronized (TUNED) {
            TUNED.clear();
        }
    }
}
