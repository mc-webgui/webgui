package land.webgui.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The limiter is the only thing standing between a page's JavaScript and the server
 * thread, so its arithmetic is worth checking directly. Touches no Minecraft classes.
 */
class WebviewRateLimiterTest {

    private static final String NAME = "Steve";

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void freshWindows() {
        WebviewRateLimiter.resetForTests();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
    }

    private static boolean allow(UUID id, long cost, long limit) {
        return WebviewRateLimiter.allow(id, NAME, WebviewRateLimiter.Bucket.PAGE_EVENTS, cost, limit);
    }

    @Test
    void allowsUpToTheLimitThenRefuses() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(allow(alice, 1, 5), "call " + i + " should be inside the limit");
        }
        assertFalse(allow(alice, 1, 5));
        assertFalse(allow(alice, 1, 5));
    }

    @Test
    void chargesEvenWhenRefused() {
        // Otherwise a page that keeps hammering slips a request through every time the
        // window happens to have room, which is exactly the flood being guarded against.
        for (int i = 0; i < 100; i++) {
            allow(alice, 1, 3);
        }
        assertFalse(allow(alice, 1, 3));
    }

    @Test
    void playersAreMeteredSeparately() {
        for (int i = 0; i < 10; i++) {
            allow(alice, 1, 2);
        }

        assertTrue(allow(bob, 1, 2), "one player's flood must not block another");
    }

    @Test
    void bucketsAreMeteredSeparately() {
        for (int i = 0; i < 10; i++) {
            WebviewRateLimiter.allow(alice, NAME, WebviewRateLimiter.Bucket.PAGE_EVENTS, 1, 2);
        }

        assertTrue(WebviewRateLimiter.allow(alice, NAME, WebviewRateLimiter.Bucket.ASSET_BYTES, 1024, 1_000_000),
                "spending the event budget must not spend the bandwidth budget");
    }

    @Test
    void costIsChargedByWeightNotByCall() {
        assertTrue(WebviewRateLimiter.allow(alice, NAME, WebviewRateLimiter.Bucket.ASSET_BYTES, 600_000, 1_000_000));

        assertFalse(WebviewRateLimiter.allow(alice, NAME, WebviewRateLimiter.Bucket.ASSET_BYTES, 600_000, 1_000_000),
                "two 600 KB files must not both fit in a 1 MB budget");
    }

    @Test
    void aLimitOfZeroDisablesTheBucket() {
        for (int i = 0; i < 10_000; i++) {
            assertTrue(allow(alice, 1, 0));
        }
    }

    @Test
    void aNullPlayerIsNeverBlocked() {
        // Console and internal senders have no UUID, and refusing them would be a bug
        // that only shows up in production.
        assertTrue(allow(null, 1, 1));
        assertTrue(allow(null, 1, 1));
    }

    @Test
    void forgettingAPlayerClearsTheirBudget() {
        for (int i = 0; i < 10; i++) {
            allow(alice, 1, 2);
        }
        assertFalse(allow(alice, 1, 2));

        WebviewRateLimiter.forget(alice);

        assertTrue(allow(alice, 1, 2));
    }

    @Test
    void aHugeCostSaturatesInsteadOfCorruptingTheWindow() {
        // The second and the amount spent share one long; a cost that overflows the
        // count must not carry into the second, or the window silently resets and the
        // limit stops working.
        assertFalse(WebviewRateLimiter.allow(alice, NAME, WebviewRateLimiter.Bucket.ASSET_BYTES, Long.MAX_VALUE, 1000));
        assertFalse(WebviewRateLimiter.allow(alice, NAME, WebviewRateLimiter.Bucket.ASSET_BYTES, 1, 1000),
                "the window must still be spent after a saturating charge");
    }
}
