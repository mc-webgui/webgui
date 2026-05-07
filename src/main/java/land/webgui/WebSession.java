package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;

public final class WebSession {
    private static MCEFBrowser browser;
    private static MCEFBrowser suspendedHudBrowser;
    private static Mode mode = Mode.NONE;

    public enum Mode {
        NONE,
        GUI_SCREEN,
        HUD_OVERLAY
    }

    private WebSession() {}

    public static MCEFBrowser browser() {
        return browser;
    }

    public static MCEFBrowser hudBrowser() {
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

    public static MCEFBrowser openForGui(String url) {
        closeSuspendedHudBrowser();
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            suspendedHudBrowser = browser;
            browser = null;
        } else {
            closeActiveBrowser();
        }
        browser = MCEF.createBrowser(url, true);
        mode = Mode.GUI_SCREEN;
        WebviewClientBridge.clearCache();
        return browser;
    }

    public static MCEFBrowser openForHud(String url) {
        closeSuspendedHudBrowser();
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            browser.loadURL(url);
            WebviewClientBridge.clearCache();
            return browser;
        }
        closeActiveBrowser();
        browser = MCEF.createBrowser(url, true);
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
