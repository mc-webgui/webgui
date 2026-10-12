/**
 * The contract a mod compiles against.
 *
 * <p>Nothing here mentions Minecraft or Chromium. That is the point: the browser engine
 * underneath can be updated, or swapped, without a consuming mod changing a line.
 *
 * <h2>Threading</h2>
 *
 * <p>Every method in this package is safe to call from any thread. Calls are queued to the
 * thread that owns the browser rather than executed where they are made.
 *
 * <p><strong>No method blocks.</strong> Anything with a result returns a
 * {@link java.util.concurrent.CompletableFuture}. This is a safety property rather than a
 * convenience: the browser message loop is pumped by the game thread, so waiting on the
 * game thread for something the browser must produce deadlocks the game. An API with no
 * blocking calls cannot express that mistake.
 *
 * <p>Callbacks are delivered on the runtime executor, which is never the game thread. The
 * Minecraft-side module re-dispatches them for you where that matters.
 */
package space.webgui.wmc.api;
