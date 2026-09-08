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
                    WebviewClientEmit.dispatch("death", death);
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
            browser.executeJavaScript(WebviewScriptInject.bridgeSetup(), url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui bridge inject: {}", t.toString());
        }
    }
}
