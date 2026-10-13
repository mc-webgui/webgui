package land.webgui;

import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;

public final class WebviewPageLoadHooks {
    private WebviewPageLoadHooks() {}

    public static void register() {
        // Attached to every browser as it opens, rather than to the engine once: java-cef
        // keeps a single load handler per client and the engine's own is in that slot.
        WebBrowser.addListener(new WebBrowser.Listener() {

            @Override
            public void onLoadStart(WebBrowser browser, String url) {
                WebBrowser active = WebSession.browser();
                if (active == null || browser != active) return;

                // Here rather than where the browser is created: the protocol needs a
                // browser that exists natively, and the first load is the earliest point
                // where that is certainly true.
                WebGUIDevTools.attachOnce(active);
                WebGUIBrowserTuning.applyOnce(active);

                // Before the document's own scripts, not after them. Injecting only at
                // load end meant a plain <script> touching window.webgui threw, because
                // inline scripts run while the document is still parsing — so the very
                // first thing anyone writes on their first page failed with
                // "Cannot read properties of undefined". It is injected again at load
                // end; the script is written to be idempotent, and a page that replaces
                // its own document would otherwise lose the bridge.
                injectBridgeScript(active);

                if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY) {
                    WebHudOverlay.onHudBrowserLoadStart(active);
                } else if (WebSession.mode() == WebSession.Mode.GUI_SCREEN) {
                    WebViewScreen.onGuiBrowserLoadStart(active);
                }
            }

            @Override
            public void onLoadEnd(WebBrowser browser, String url, int httpStatusCode) {
                WebBrowser active = WebSession.browser();
                if (active == null || browser != active) return;

                injectBridgeScript(active);

                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> WebviewClientBridge.pushAfterDocumentLoad(mc));
                }

                // The death payload lands while this page is still being created, so
                // an immediate emit would arrive before any listener exists. Replay it
                // now that the document is up.
                String death = WebGUIDeathScreen.info();
                if (death != null && WebGUIDeathScreen.active()) {
                    WebviewClientEmit.dispatchDeath(death);
                }

                if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY) {
                    WebHudOverlay.onHudBrowserLoadFinished(active);
                } else if (WebSession.mode() == WebSession.Mode.GUI_SCREEN) {
                    WebViewScreen.onGuiBrowserLoadFinished(active);
                }
            }

            @Override
            public void onLoadFailed(WebBrowser browser, String failedUrl, int errorCode,
                                     String errorText) {
                WebBrowser active = WebSession.browser();
                if (active == null || browser != active) return;

                // A death page that will not load would otherwise leave the player
                // staring at nothing with no way to respawn, so let Escape out.
                if (WebGUIDeathScreen.active()) {
                    WebGUIDeathScreen.setLoadFailed(true);
                    WebGUIMod.LOGGER.warn("webgui: death page failed to load ({}): {} — Escape will respawn instead",
                            errorText, failedUrl);
                }

                if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY) {
                    WebHudOverlay.onHudBrowserLoadFinished(active);
                } else if (WebSession.mode() == WebSession.Mode.GUI_SCREEN) {
                    WebViewScreen.onGuiBrowserLoadFinished(active);
                }
            }
        });
    }

    private static void injectBridgeScript(WebBrowser browser) {
        try {
            String url = browser.getURL();
            browser.executeJavaScript(WebviewScriptInject.bridgeSetup(WebGUIAssetServer.base(), WebSession.mode() == WebSession.Mode.HUD_OVERLAY), url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui bridge inject: {}", t.toString());
        }
    }
}
