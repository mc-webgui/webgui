package space.webgui.wmc.api;

/**
 * What a view reports while it runs. Every method is optional.
 *
 * <p>Failures are separate from loads on purpose. A page that fails to load still fires
 * {@link #onLoadEnd}, so a mod that only listens to that cannot tell a working page from a
 * blank one, which is how a broken URL becomes a silent black rectangle.
 */
public interface WebViewListener {

    default void onLoadStart(String url) {
    }

    default void onLoadEnd(String url, int httpStatus) {
    }

    /**
     * The navigation failed.
     *
     * @param errorCode the Chromium network error code, negative
     */
    default void onLoadFailed(String url, int errorCode, String description) {
    }

    /** The page changed its title. */
    default void onTitle(String title) {
    }

    /** The page asked for a different cursor, named as in CSS. */
    default void onCursor(String cssCursor) {
    }

    /**
     * A file is being downloaded, or has finished.
     *
     * <p>Called as it goes, so a mod can show progress, and once more when it stops. Where the
     * file is written is settled before the first of these — by the runtime's settings, or by
     * the person, if they were asked.
     */
    default void onDownload(Download download) {
    }

    /**
     * The renderer process for this view died.
     *
     * <p>Worth handling: the view stays alive but paints nothing, so without this the page
     * simply freezes with no explanation.
     */
    default void onRendererGone(String reason) {
    }

    /** A message the page logged, forwarded so it can reach the game log. */
    default void onConsole(ConsoleLevel level, String message, String source, int line) {
    }
}
