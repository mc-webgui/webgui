package space.webgui.wmc.api;

import java.util.concurrent.CompletableFuture;

/**
 * The browser runtime: one per game.
 *
 * <p>Obtaining it never fails and never blocks. A runtime that has not started, or that
 * failed to start, still answers {@link #state()} and {@link #whenReady()}. Attach to
 * {@link #whenReady()} rather than polling.
 */
public interface WebRuntime {

    /** The runtime for this game, never {@code null}, possibly {@link RuntimeState#UNAVAILABLE}. */
    static WebRuntime get() {
        return RuntimeHolder.current();
    }

    /** What the runtime is doing, for a mod that wants to show it. */
    RuntimeState state();

    /**
     * Completes when views can be opened, or completes exceptionally if the runtime cannot
     * start. Safe to attach to at any time, including after the fact.
     */
    CompletableFuture<Void> whenReady();

    /** Opens a view. Completes once the view exists, not once a page has loaded. */
    CompletableFuture<WebView> open(WebViewSpec spec);

    /**
     * Serves a URL scheme of your own, such as {@code app://}.
     *
     * <p>Must be called before the runtime starts, because Chromium requires custom schemes
     * to be registered before its first process comes up. The scheme is registered as
     * standard, secure and CORS-enabled, so a page loaded from it gets a real origin and
     * {@code fetch}, ES modules and storage behave as they would over https.
     *
     * @throws IllegalStateException if the runtime has already started
     */
    void registerScheme(String scheme, SchemeHandler handler);

    /** Versions and platform, for logs and bug reports. */
    RuntimeInfo info();

    /** Observes downloads and start-up, for a mod that wants to draw progress. */
    void addListener(RuntimeListener listener);

    void removeListener(RuntimeListener listener);
}
