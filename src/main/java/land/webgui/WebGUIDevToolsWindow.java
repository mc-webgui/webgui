package land.webgui;

import land.webgui.browser.WebBrowser;

/**
 * The inspector, in a window of its own, the way a browser opens it.
 *
 * Chromium opens that window itself: the request goes to its own ShowDevTools, which is a
 * Chromium pop-up and not something the game or AWT has to host, and it paints from the
 * message loop the client already drives. So there is nothing here but the asking.
 *
 * Different from {@link WebGUIDevTools}, which puts the page's own complaints in the game log
 * and needs no window at all.
 */
public final class WebGUIDevToolsWindow {

    private WebGUIDevToolsWindow() {}

    /** Whether there was a page to ask about. The browser opens a moment later. */
    public static boolean open(WebBrowser browser) {
        if (browser == null) {
            return false;
        }
        browser.openDevTools(why -> {
            WebGUIMod.LOGGER.warn("webgui devtools: no inspector to open ({})", why);
            // Said to the player, not only to the log. A key that answers nothing at all is
            // indistinguishable from a key that is broken, and this one is off by default.
            land.webgui.compat.Mc.tell("Inspector is off. Turn on \"Page developer tools\" in the mod settings"
                    + " and restart the game.");
        });
        return true;
    }

}
