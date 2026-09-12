package land.webgui;

import de.keksuccino.rinku.RinkuBrowser;

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

    /** Whether there was a page to open them for. */
    public static boolean open(RinkuBrowser browser) {
        if (browser == null) {
            return false;
        }
        try {
            browser.openDevTools();
            WebGUIMod.LOGGER.info("webgui devtools: opened the inspector for {}", browser.getURL());
            return true;
        } catch (Throwable t) {
            WebGUIMod.LOGGER.warn("webgui devtools: could not open the inspector: {}", t.toString());
            return false;
        }
    }
}
