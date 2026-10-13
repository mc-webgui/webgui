package space.webgui.wmc.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A named, two-way message channel between a view and the mod that opened it.
 *
 * <p>This exists because every mod that embeds a browser ends up writing it: a way for the
 * page to ask the game something, a way for the game to push updates, and a set of rules
 * about what a page is allowed to do. Written once per mod, that layer is where the
 * mistakes live. Written here, it has one policy model and one set of limits.
 *
 * <p>Payloads are strings, by convention JSON. The API does not parse them: a mod already
 * has a JSON library and its own types, and a second opinion in the middle helps nobody.
 *
 * <p>The page side is injected before any script on the page runs, so a plain inline script
 * can use it without waiting for an event.
 *
 * <pre>{@code
 * const shop = wmc.channel('shop');
 * const stock = await shop.request(JSON.stringify({ item: 'torch' }));
 * shop.on(update => render(JSON.parse(update)));
 * }</pre>
 */
public interface Channel {

    /** The name shared by both sides. */
    String name();

    /**
     * Applies a policy, returning the same channel for chaining.
     *
     * <p>Until this is called the channel is closed: a page can open it, and nothing it
     * sends is delivered. Deny-by-default is deliberate, because a channel is a hole through
     * which a page reaches the game.
     */
    Channel withPolicy(ChannelPolicy policy);

    /**
     * Handles a request from the page.
     *
     * <p>The handler returns a future so that answering can take as long as it needs without
     * blocking anything. Completing it exceptionally rejects the request, and the page sees a
     * rejected promise rather than a hang.
     */
    void onRequest(Function<String, CompletableFuture<String>> handler);

    /** Handles a message the page sent without expecting an answer. */
    void onEvent(Consumer<String> handler);

    /**
     * Asks the page something.
     *
     * <p>Completes exceptionally if the page has no handler, if it takes longer than the
     * policy timeout, or if the view closes first.
     */
    CompletableFuture<String> request(String payload);

    /** Sends the page a message, without waiting. */
    void emit(String payload);

    /** Stops delivery in both directions. The page sees the channel close. */
    void close();
}
