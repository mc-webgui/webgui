package land.webgui;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
//? if fabric {
import net.minecraft.client.MinecraftClient;
//? } else {
/*import net.minecraft.client.Minecraft;*/
//? }
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;

public final class WebviewPageLoadHooks {
    private WebviewPageLoadHooks() {}

    public static void register() {
        Rinku.getClient().addLoadHandler(new CefLoadHandlerAdapter() {

            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame,
                                    org.cef.network.CefRequest.TransitionType transitionType) {
                RinkuBrowser active = WebSession.browser();
                if (active == null || browser != active) return;

                // Here rather than where the browser is created: the protocol needs a
                // browser that exists natively, and the first load is the earliest point
                // where that is certainly true.
                WebGUIDevTools.attachOnce(active);

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
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                RinkuBrowser active = WebSession.browser();
                if (active == null || browser != active) return;

                injectBridgeScript(active);

                //? if fabric {
                MinecraftClient mc = MinecraftClient.getInstance();
                //? } else {
                /*Minecraft mc = Minecraft.getInstance();*/
                //? }
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
            public void onLoadError(CefBrowser browser, CefFrame frame,
                                    CefLoadHandler.ErrorCode errorCode,
                                    String errorText, String failedUrl) {
                RinkuBrowser active = WebSession.browser();
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

    private static void injectBridgeScript(RinkuBrowser browser) {
        try {
            String url = browser.getURL();
            browser.executeJavaScript(WebviewScriptInject.bridgeSetup(WebGUIAssetServer.base(), WebSession.mode() == WebSession.Mode.HUD_OVERLAY), url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui bridge inject: {}", t.toString());
        }
    }
}
