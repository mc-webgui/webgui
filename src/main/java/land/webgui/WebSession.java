package land.webgui;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;

public final class WebSession {
    private static RinkuBrowser browser;
    private static RinkuBrowser suspendedHudBrowser;
    private static Mode mode = Mode.NONE;

    public enum Mode {
        NONE,
        GUI_SCREEN,
        HUD_OVERLAY
    }

    private WebSession() {}

    public static RinkuBrowser browser() {
        return browser;
    }

    public static RinkuBrowser hudBrowser() {
        if (mode == Mode.HUD_OVERLAY) {
            return browser;
        }
        if (mode == Mode.GUI_SCREEN) {
            return suspendedHudBrowser;
        }
        return null;
    }

    public static Mode mode() {
        return mode;
    }

    public static void dispose() {
        closeActiveBrowser();
        closeSuspendedHudBrowser();
        mode = Mode.NONE;
        WebviewClientBridge.clearCache();
    }

    private static void closeActiveBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    private static void closeSuspendedHudBrowser() {
        if (suspendedHudBrowser != null) {
            suspendedHudBrowser.close();
            suspendedHudBrowser = null;
        }
    }

    public static void closeGuiAndRestoreHud() {
        if (mode != Mode.GUI_SCREEN) {
            return;
        }
        closeActiveBrowser();
        if (suspendedHudBrowser != null) {
            browser = suspendedHudBrowser;
            suspendedHudBrowser = null;
            mode = Mode.HUD_OVERLAY;
        } else {
            mode = Mode.NONE;
        }
        WebviewClientBridge.clearCache();
    }

    public static RinkuBrowser openForGui(String url) {
        closeSuspendedHudBrowser();
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            suspendedHudBrowser = browser;
            browser = null;
        } else {
            closeActiveBrowser();
        }
        browser = Rinku.createBrowser(WebGUIAssetServer.resolve(url), true);
        mode = Mode.GUI_SCREEN;
        WebviewClientBridge.clearCache();
        return browser;
    }

    public static RinkuBrowser openForHud(String url) {
        closeSuspendedHudBrowser();
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            browser.loadURL(WebGUIAssetServer.resolve(url));
            WebviewClientBridge.clearCache();
            return browser;
        }
        closeActiveBrowser();
        browser = Rinku.createBrowser(WebGUIAssetServer.resolve(url), true);
        mode = Mode.HUD_OVERLAY;
        WebviewClientBridge.clearCache();
        return browser;
    }

    public static void closeHudOnly() {
        if (mode == Mode.HUD_OVERLAY) {
            closeActiveBrowser();
            mode = Mode.NONE;
        }
        closeSuspendedHudBrowser();
    }
}
