package space.webgui.wmc.api;

/**
 * One browser view, rendered off-screen.
 *
 * <p>A view has no window and draws nothing by itself. It paints into a {@link Frame} that
 * the caller uploads wherever it wants; the Minecraft-side module does that for you.
 *
 * <p>Closing a view releases its browser. A closed view throws on every method except
 * {@link #isClosed()}.
 */
public interface WebView extends AutoCloseable {

    /** Navigates. Relative URLs are resolved against the current one. */
    void load(String url);

    /**
     * Loads markup directly.
     *
     * @param baseUrl the origin the page believes it came from, which decides what its
     *                relative URLs, storage and {@code fetch} calls can reach
     */
    void loadHtml(String html, String baseUrl);

    /** The URL currently committed, which lags {@link #load} until the navigation commits. */
    String url();

    void reload(boolean ignoreCache);

    void stop();

    /** Resizes the view in CSS pixels. Cheap to call every frame; a no-op when unchanged. */
    void resize(int width, int height);

    /**
     * Caps how often the view repaints, in frames per second.
     *
     * <p>Exposed because the default is 30 and nothing above it is free: a page that only
     * shows a number does not need 144, and an overlay tracking the camera does.
     */
    void setFrameRate(int framesPerSecond);

    /**
     * Runs script in the page.
     *
     * <p>Nothing comes back. What the script decides is told to the game over a channel, the
     * same way the page tells it anything else — a return value here would have to be waited
     * for, and waiting on the thread that drives the browser is the deadlock this API is
     * shaped to make impossible.
     *
     * @param sourceUrl what the page should call this script when it reports an error in it,
     *                  which is the difference between a stack trace that names something and
     *                  one that says {@code undefined}
     * @param startLine the line the script starts at, for the same reason
     */
    void evaluate(String script, String sourceUrl, int startLine);

    /**
     * Scales the page, the way a browser's own zoom does.
     *
     * <p>Zero is unscaled and each step is a factor of 1.2, which is Chromium's scale, not a
     * multiplier: a game drawing its interface at 2× wants about 3.8 here, not 2.
     */
    void setZoom(double level);

    /** The most recently painted frame. Never {@code null}; may be empty before the first paint. */
    Frame frame();

    Input input();

    /**
     * A named message channel to the page, created on first use.
     *
     * <p>Channels start closed to everything. Grant what a page may do with
     * {@link Channel#withPolicy(ChannelPolicy)} rather than trusting its origin by default.
     */
    Channel channel(String name);

    Devtools devtools();

    void addListener(WebViewListener listener);

    void removeListener(WebViewListener listener);

    /**
     * Whether the page has a menu of its own open over it, such as an open select box.
     *
     * <p>Worth asking before acting on a key that means "close this". A player pressing escape
     * with a dropdown open means the dropdown; whoever draws the view is the only one who knows
     * what escape would otherwise do, so the decision stays theirs and this is the fact they
     * need to make it.
     */
    default boolean isPopupOpen() {
        return false;
    }

    boolean isClosed();

    @Override
    void close();
}
