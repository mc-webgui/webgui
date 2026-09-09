package land.webgui.server;

import land.webgui.WebGUIMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caps how fast one player's pages may talk to the server.
 *
 * The page-event channel is the only thing a client can push at a WebGUI server, and a
 * page is web content: a loop in someone's JavaScript — or a hostile page a player was
 * talked into opening — can fire it as fast as the socket allows, and every message is
 * dispatched on the server thread. Without a ceiling that is a tick-rate denial of
 * service that any player can trigger from a browser.
 *
 * A fixed window rather than a token bucket: it needs one long per player, and the
 * failure it guards against is a flood, not a precisely-shaped burst.
 */
public final class WebviewRateLimiter {

    /** Stops a churn of joining players from growing the map without bound. */
    private static final int MAX_TRACKED = 1000;

    /**
     * nanoTime is only meaningful as a difference — its absolute value may be negative —
     * and a negative second would not survive being packed into the high bits below,
     * leaving every call looking like a fresh window and the limit never firing.
     */
    private static final long START_NANOS = System.nanoTime();

    private static final Map<UUID, Window> WINDOWS = new ConcurrentHashMap<>();

    private WebviewRateLimiter() {}

    private static final class Window {
        final AtomicLong state = new AtomicLong();  // high 42 bits: second; low 22: count
        volatile boolean warned;
    }

    /**
     * Whether this event may be handled. Counts the call either way, so a page that keeps
     * hammering stays blocked for the rest of its second.
     */
    public static boolean allow(UUID playerId, String playerName) {
        int limit = WebviewServerConfig.pageEventsPerSecond();
        if (limit <= 0 || playerId == null) {
            return true;
        }
        if (WINDOWS.size() > MAX_TRACKED) {
            WINDOWS.clear();
        }

        Window w = WINDOWS.computeIfAbsent(playerId, id -> new Window());
        long nowSecond = (System.nanoTime() - START_NANOS) / 1_000_000_000L;
        long updated = w.state.updateAndGet(prev -> {
            long second = prev >>> 22;
            long count = prev & 0x3F_FFFF;
            if (second != nowSecond) {
                return (nowSecond << 22) | 1L;
            }
            // Saturate rather than wrap into the second field.
            return (second << 22) | Math.min(count + 1, 0x3F_FFFF);
        });

        long count = updated & 0x3F_FFFF;
        if (count <= limit) {
            w.warned = false;
            return true;
        }
        if (!w.warned) {
            w.warned = true;
            WebGUIMod.LOGGER.warn(
                    "webgui: dropping page events from {} — over {}/s. Raise or disable pageEventsPerSecond in config/webgui/server.json if this is legitimate.",
                    playerName, limit);
        }
        return false;
    }

    /** Called when a player leaves, so their window does not outlive the session. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            WINDOWS.remove(playerId);
        }
    }
}
