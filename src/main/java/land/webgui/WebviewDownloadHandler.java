package land.webgui;

import land.webgui.browser.WebBrowser;
import space.webgui.wmc.api.Download;

/**
 * Tells the player about a file a page handed them.
 *
 * <p>Saving it is the engine's job now, and so is choosing a safe name for it — a suggested
 * name is the one part of a download that web content fully controls, and it is about to
 * become a path. What is left here is the part that was always this mod's: saying so. A file
 * that arrives with no trace is a file the player will never find, and the folder is not one
 * they know about.
 *
 * <p>Where it lands is set once, in {@link land.webgui.browser.BrowserBoot}, rather than
 * decided per download by a handler racing another mod's for the right to answer.
 */
public final class WebviewDownloadHandler {

    private static final String FOLDER = "webgui-downloads";

    private WebviewDownloadHandler() {}

    public static void register() {
        WebBrowser.addListener(new WebBrowser.Listener() {
            @Override
            public void onDownload(WebBrowser browser, Download download) {
                if (download.done()) {
                    WebGUIMod.LOGGER.info("webgui: downloaded {}", download.path());
                    land.webgui.compat.Mc.tell("Saved to " + FOLDER + "/" + fileName(String.valueOf(download.path())));
                } else if (download.canceled()) {
                    WebGUIMod.LOGGER.info("webgui: download cancelled ({})", download.url());
                }
            }
        });
    }

    private static String fileName(String fullPath) {
        if (fullPath == null) {
            return "?";
        }
        int slash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return slash >= 0 ? fullPath.substring(slash + 1) : fullPath;
    }

}
