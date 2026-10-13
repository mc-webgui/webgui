package space.webgui.wmc.api;

import java.util.concurrent.CompletableFuture;

/**
 * Serves a URL scheme of your own, such as {@code app://}.
 *
 * <p>This is what removes the local web server. Without it, a mod that wants to show files
 * it ships has to run an HTTP server on a loopback port, find a free port, guard it with a
 * token, and then explain to its users why the page origin is {@code 127.0.0.1} and their
 * API has to allow it in CORS. With it, the files are simply served.
 *
 * <p>The scheme is registered as standard, secure and CORS-enabled, so a page loaded from it
 * is a proper origin: {@code fetch}, ES modules, service workers and storage all behave as
 * they would over https.
 *
 * <p>Handlers are called off the game thread and may take as long as they need. Returning a
 * failed future produces a network error in the page rather than a hang.
 */
@FunctionalInterface
public interface SchemeHandler {

    CompletableFuture<Resource> resolve(SchemeRequest request);
}
