package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
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
        MCEF.getClient().addLoadHandler(new CefLoadHandlerAdapter() {

            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame,
                                    org.cef.network.CefRequest.TransitionType transitionType) {
                MCEFBrowser active = WebSession.browser();
                if (active == null || browser != active) return;
                if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY) {
                    WebHudOverlay.onHudBrowserLoadStart(active);
                } else if (WebSession.mode() == WebSession.Mode.GUI_SCREEN) {
                    WebViewScreen.onGuiBrowserLoadStart(active);
                }
            }

            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                MCEFBrowser active = WebSession.browser();
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
                MCEFBrowser active = WebSession.browser();
                if (active == null || browser != active) return;
                if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY) {
                    WebHudOverlay.onHudBrowserLoadFinished(active);
                } else if (WebSession.mode() == WebSession.Mode.GUI_SCREEN) {
                    WebViewScreen.onGuiBrowserLoadFinished(active);
                }
            }
        });
    }

    private static void injectBridgeScript(MCEFBrowser browser) {
        try {
            String url = browser.getURL();
            browser.executeJavaScript(WebviewScriptInject.bridgeSetup(), url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui bridge inject: {}", t.toString());
        }
    }
}
