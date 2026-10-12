package land.webgui;

/**
 * Where a page opens when nobody said where.
 *
 * <p>Reached whenever a URL is missing rather than wrong: the main-menu key pressed on the
 * title screen, where no server has sent its settings yet, or a server that opened a page
 * without naming one. The mod's own site explains what to set; it used to be
 * {@code example.com}, which explained nothing and looked like a bug.
 */
public final class StartUrls {

    public static final String HTTPS_FALLBACK = "https://webgui.space";

    private StartUrls() {}

    public static String primary() {
        return HTTPS_FALLBACK;
    }
}
