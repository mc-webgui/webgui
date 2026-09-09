package land.webgui.server;

import land.webgui.WebGUIMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caps how much of the server one player's pages may consume per second.
 *
 * Everything a client can push at a WebGUI server comes from web content: a loop in
 * someone's JavaScript — or a hostile page a player was talked into opening — can fire
 * as fast as the socket allows, and the work lands on the server thread. Without a
 * ceiling that is a tick-rate denial of service any player can trigger from a browser.
 *
 * Two things are metered, in separate buckets, because they fail differently: page
 * events cost server-thread time and are counted one by one, while asset requests cost
 * bandwidth and are counted in bytes.
 *
 * A fixed window rather than a token bucket: it needs one long per player per bucket,
 * and the failure it guards against is a flood, not a precisely-shaped burst.
 */
public final class WebviewRateLimiter {

    /** What is being metered. */
    public enum Bucket {
        /** {@code postToGame} messages, counted one each. */
        PAGE_EVENTS("page events", "pageEventsPerSecond"),
        /** Bundled page bytes, counted by file size. */
        ASSET_BYTES("asset bytes", "assetBytesPerSecond");

        final String what;
        final String setting;

        Bucket(String what, String setting) {
            this.what = what;
            this.setting = setting;
        }
    }

    /** Stops a churn of joining players from growing the map without bound. */
    private static final int MAX_TRACKED = 1000;

    /**
     * nanoTime is only meaningful as a difference — its absolute value may be negative —
     * and a negative second would not survive being packed into the high bits below,
     * leaving every call looking like a fresh window and the limit never firing.
     */
    private static final long START_NANOS = System.nanoTime();

    /** 26 bits of count: enough for a bandwidth budget in bytes, not just a message tally. */
    private static final int COUNT_BITS = 26;
    private static final long COUNT_MASK = (1L << COUNT_BITS) - 1;

    private static final Map<UUID, Window[]> WINDOWS = new ConcurrentHashMap<>();

    private WebviewRateLimiter() {}

    private static final class Window {
        /** High bits: the second this window covers. Low bits: what has been spent in it. */
        final AtomicLong state = new AtomicLong();
        volatile boolean warned;
    }

    /** Convenience for the page-event channel, whose limit lives in the config. */
    public static boolean allowPageEvent(UUID playerId, String playerName) {
        return allow(playerId, playerName, Bucket.PAGE_EVENTS, 1, WebviewServerConfig.pageEventsPerSecond());
    }

    /**
     * Whether this much may be spent. Charges the cost either way, so something that
     * keeps hammering stays blocked for the rest of its second rather than slipping
     * through whenever the budget briefly allows.
     *
     * @param cost             what this call consumes — one message, or a file's bytes
     * @param limitPerSecond   the ceiling; zero or less disables the bucket
     */
    public static boolean allow(UUID playerId, String playerName, Bucket bucket, long cost, long limitPerSecond) {
        if (limitPerSecond <= 0 || playerId == null) {
            return true;
        }
        if (WINDOWS.size() > MAX_TRACKED) {
            WINDOWS.clear();
        }

        Window[] buckets = WINDOWS.computeIfAbsent(playerId, id -> new Window[Bucket.values().length]);
        Window w = buckets[bucket.ordinal()];
        if (w == null) {
            // Racing here at worst creates a second window and loses a few counts of a
            // single second, which is not worth a lock on every asset request.
            w = new Window();
            buckets[bucket.ordinal()] = w;
        }

        long nowSecond = (System.nanoTime() - START_NANOS) / 1_000_000_000L;
        long charge = Math.max(1L, cost);
        final Window window = w;
        long updated = window.state.updateAndGet(prev -> {
            long second = prev >>> COUNT_BITS;
            long spent = prev & COUNT_MASK;
            if (second != nowSecond) {
                return (nowSecond << COUNT_BITS) | Math.min(charge, COUNT_MASK);
            }
            // Saturate rather than wrap into the second field.
            return (second << COUNT_BITS) | Math.min(spent + charge, COUNT_MASK);
        });

        long spent = updated & COUNT_MASK;
        if (spent <= limitPerSecond) {
            window.warned = false;
            return true;
        }
        if (!window.warned) {
            window.warned = true;
            WebGUIMod.LOGGER.warn(
                    "webgui: dropping {} from {} — over {}/s. Raise or disable {} in config/webgui/server.json if this is legitimate.",
                    bucket.what, playerName, limitPerSecond, bucket.setting);
        }
        return false;
    }

    /** Called when a player leaves, so their windows do not outlive the session. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            WINDOWS.remove(playerId);
        }
    }

    /** Test seam: drops every window so one test cannot inherit another's budget. */
    static void resetForTests() {
        WINDOWS.clear();
    }
}
