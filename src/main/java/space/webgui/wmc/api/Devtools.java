package space.webgui.wmc.api;

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

/**
 * The page inspector.
 *
 * <p>Present in the API because a page inside a game has no inspector, and without one a
 * blank page leaves nothing to go on. Turning it on opens a remote debugging port, so any
 * Chromium browser on the machine can attach to the page running in the game.
 *
 * <p>Off unless asked for: the port is a way into the game process.
 */
public interface Devtools {

    /**
     * Starts listening, or returns the port already in use.
     *
     * <p>Pass {@code 0} to let the operating system pick one.
     */
    CompletableFuture<Integer> enable(int port);

    /** The port being listened on, if any. */
    OptionalInt port();

    /**
     * Sends one Chrome DevTools Protocol method to this view.
     *
     * <p>The escape hatch for everything not modelled here. Completes with the raw JSON
     * result, and never blocks: the protocol is answered on the browser thread, which is the
     * thread a caller would otherwise be tempted to wait on.
     */
    CompletableFuture<String> call(String method, String paramsJson);

    /**
     * Listens to what the page reports over the protocol, rather than asking it.
     *
     * <p>The half of the protocol {@link #call} cannot reach: a console message, an uncaught
     * exception, a request that failed. Each domain stays silent until it is enabled, so this
     * is paired with a {@code call} to {@code Log.enable}, {@code Runtime.enable} or whichever
     * domain is wanted — subscribing alone reports nothing.
     *
     * <p>The handler is given the event's method name and its parameters as JSON, and is
     * called on the thread the protocol answers on, which is not the game's.
     */
    void onEvent(java.util.function.BiConsumer<String, String> handler);

    /**
     * The address of the developer tools for this view, for opening in a browser.
     *
     * <p>Issued by the browser engine and specific to this view, so that with several views
     * open the tools land on the right one. Fails when the tools are off.
     */
    CompletableFuture<String> frontendUrl();

    /**
     * Opens the inspector in a window of its own, the way pressing F12 in a browser does.
     *
     * <p>Needs no debugging port: the window belongs to the browser engine, which opens and
     * draws it itself, and it inspects this view whatever else is open. The one thing it does
     * need is the message loop the game already drives, because that is what paints it.
     *
     * <p>Returns at once. There is no callback for the window appearing, and nothing useful to
     * do while waiting for one.
     */
    void openWindow();

    /** Closes that window, if it is open. */
    void closeWindow();

    void disable();
}
