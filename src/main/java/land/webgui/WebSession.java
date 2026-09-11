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
        if (mode == Mode.HUD_OVERLAY && browser != null) {
            // Coming from the hud: park it. Nothing should already be parked in this mode,
            // but closing first keeps that from becoming a leak if it ever is.
            closeSuspendedHudBrowser();
            suspendedHudBrowser = browser;
            browser = null;
        } else {
            // One gui replacing another, which is ordinary: dying with a gui open does it.
            // Only the outgoing gui is closed here - whatever hud is parked belongs to
            // whoever opened the first gui, and closing it here left the player with no
            // hud at all once they escaped out.
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

    /**
     * Refreshes any open page that came from the server's own files.
     *
     * The address does not change across a reload, only the bytes behind it, so nothing
     * would make the browser ask again on its own. A page from a web host is left alone.
     */
    public static void reloadBundledPages() {
        String origin = WebGUIAssetServer.origin();
        if (origin.isEmpty()) {
            return;
        }
        boolean any = reloadIfBundled(browser, origin) | reloadIfBundled(suspendedHudBrowser, origin);
        if (any) {
            // The bridge only pushes what changed, and a reloaded page has nothing.
            WebviewClientBridge.clearCache();
        }
    }

    private static boolean reloadIfBundled(RinkuBrowser target, String origin) {
        if (target == null) {
            return false;
        }
        String url = target.getURL();
        if (url == null || !url.startsWith(origin)) {
            return false;
        }
        target.reload();
        return true;
    }

    public static void closeHudOnly() {
        if (mode == Mode.HUD_OVERLAY) {
            closeActiveBrowser();
            mode = Mode.NONE;
        }
        closeSuspendedHudBrowser();
    }
}
