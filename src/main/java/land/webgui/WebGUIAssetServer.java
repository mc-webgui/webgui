package land.webgui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import land.webgui.server.WebviewAssetStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.Executors;

/**
 * Hands the server's pages to the in-game browser over loopback.
 *
 * The browser has to read them from somewhere, and a plain HTTP origin is the only form
 * every web API treats as ordinary. A custom scheme would avoid the socket, but
 * non-standard schemes are second-class in Chromium — storage, fetch and service workers
 * all behave differently — and debugging that through a game window is miserable.
 *
 * Bound to loopback only, so nothing is exposed to the network, and every URL carries a
 * per-session token: another process on the machine cannot enumerate what a server sent
 * us, and a page left over from a previous server cannot read the current one's files.
 *
 * The port is fixed by default rather than random, because a page served from here has
 * origin {@code http://127.0.0.1:25580} — and a backend that wants to accept its API
 * calls has to name that origin in CORS. A port that moved every launch would make that
 * impossible to configure.
 */
public final class WebGUIAssetServer {

    /** The host the socket binds and every URL names. They must be the same string. */
    private static final String LOOPBACK = "127.0.0.1";

    public static final int DEFAULT_PORT = 25580;
    private static final int PORT_ATTEMPTS = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static HttpServer server;
    private static int port;
    private static String sessionToken = "";
    /** Paths already reported as missing, so one broken build logs each name once. */
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private WebGUIAssetServer() {}

    /** The origin pages are served from, or empty when the server is not running. */
    public static String origin() {
        return server == null ? "" : "http://" + LOOPBACK + ":" + port;
    }

    /** The base a page prepends to a bundled path, with the session token. */
    public static String base() {
        return server == null ? "" : origin() + "/" + sessionToken;
    }

    public static boolean running() {
        return server != null;
    }

    /**
     * Starts the server if it is not already up, and mints a session token if there is none.
     *
     * Called when a manifest arrives rather than at launch: a client that never joins a
     * server that ships pages should not be listening on anything at all.
     *
     * The token is minted once per session and kept, not rotated on every manifest. A
     * server may send a second manifest while the player is in the world — {@code /webgui
     * reload} does exactly that — and rotating here would move the origin out from under
     * every page already open, so an operator editing a file watched their HUD turn into
     * a 404. Leaving a server clears the token, which is what actually has to invalidate.
     */
    public static synchronized void ensureStarted() {
        if (sessionToken.isEmpty()) {
            rotateToken();
        }
        if (server != null) {
            return;
        }
        for (int candidate = DEFAULT_PORT; candidate < DEFAULT_PORT + PORT_ATTEMPTS; candidate++) {
            try {
                // Explicitly IPv4 loopback, not getLoopbackAddress(): on a machine with
                // IPv6 that returns ::1, so the socket would listen on [::1] while every
                // URL built below says 127.0.0.1. The port shows as listening and every
                // connection to it is refused — which looked exactly like the mod's page
                // server not working at all, on one loader and not the other.
                HttpServer started = HttpServer.create(
                        new InetSocketAddress(InetAddress.getByName(LOOPBACK), candidate), 0);
                started.createContext("/", WebGUIAssetServer::handle);
                // A pool, not the caller thread: every request blocks while its file
                // comes down the game connection, and a page loads dozens at once.
                started.setExecutor(Executors.newFixedThreadPool(8, r -> {
                    Thread t = new Thread(r, "webgui-assets");
                    t.setDaemon(true);
                    return t;
                }));
                started.start();
                server = started;
                port = candidate;
                WebGUIMod.LOGGER.info("webgui: serving the server's pages at {}", origin());
                return;
            } catch (IOException e) {
                // Port taken - most likely a second instance of the game on this machine.
            }
        }
        WebGUIMod.LOGGER.error("webgui: no free port in {}..{} for the page server; bundled pages will not load",
                DEFAULT_PORT, DEFAULT_PORT + PORT_ATTEMPTS - 1);
    }

    /** Called on disconnect: the previous server's token must stop working immediately. */
    public static synchronized void invalidateSession() {
        sessionToken = "";
        WARNED.clear();
    }

    private static void rotateToken() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        sessionToken = HexFormat.of().formatHex(raw);
    }

    /**
     * Turns {@code webgui:/path} into a URL the browser can load.
     *
     * Anything else is returned untouched — an http(s) URL is a page hosted the ordinary
     * way, and that has to keep working exactly as it did.
     */
    public static String resolve(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (!trimmed.regionMatches(true, 0, "webgui:", 0, 7)) {
            return trimmed;
        }
        String rest = trimmed.substring(7);
        // Accept webgui:/index.html, webgui://index.html and webgui:index.html alike:
        // the difference is invisible in a config file and nobody would guess right.
        String query = "";
        int mark = rest.indexOf('?');
        if (mark >= 0) {
            query = rest.substring(mark);
            rest = rest.substring(0, mark);
        }
        String path = WebviewAssetStore.normalizeRequest(rest);
        if (path.isEmpty()) {
            path = "index.html";
        }
        if (!running()) {
            // Two very different causes, and blaming the wrong one sent an operator to
            // check a config file that was fine.
            if (WebGUIAssetCache.isEmpty()) {
                WebGUIMod.LOGGER.warn("webgui: {} cannot be opened - this server ships no pages", url);
            } else {
                WebGUIMod.LOGGER.warn("webgui: {} cannot be opened - the server sent {} page file(s), but no local"
                        + " port in {}..{} was free to serve them from", url, WebGUIAssetCache.size(),
                        DEFAULT_PORT, DEFAULT_PORT + PORT_ATTEMPTS - 1);
            }
            return trimmed;
        }
        return base() + "/" + path + query;
    }

    // --- serving --------------------------------------------------------------

    private static void handle(HttpExchange exchange) {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                respond(exchange, 405, "text/plain; charset=utf-8", "Only GET".getBytes(StandardCharsets.UTF_8), null, false);
                return;
            }

            String raw = exchange.getRequestURI().getPath();
            WebGUIMod.LOGGER.debug("webgui: http request {}", raw);
            String token = sessionToken;
            String prefix = "/" + token + "/";
            if (token.isEmpty() || !raw.startsWith(prefix)) {
                // Either nothing is connected, or this is a stale page from the last
                // server. Both are "not yours to read", and both are a 404 rather than a
                // 403 so nothing here confirms what does exist.
                warnOnce(raw, "webgui: page asset {} was requested without this session's prefix, so it cannot be served."
                        + " A build that emits absolute paths like /assets/app.js does this - rebuild it with a"
                        + " relative base (Vite base: './', CRA homepage: '.'). Requested by: {}",
                        raw, referrerOf(exchange));
                respond(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8), null, false);
                return;
            }

            String path = WebviewAssetStore.normalizeRequest(raw.substring(prefix.length()));
            if (path.isEmpty()) {
                path = "index.html";
            }

            WebviewAssetStore.Asset asset = WebGUIAssetCache.lookup(path);
            if (asset == null) {
                // Warned, not silent: before this, a page with one mistyped path came up
                // blank with nothing anywhere to say which file was missing.
                warnOnce(path, "webgui: {} is not in this server's pages - check the name and its case,"
                        + " and the server log for files skipped during the scan. Requested by: {}",
                        path, referrerOf(exchange));
                respond(exchange, 404, "text/plain; charset=utf-8",
                        ("Not in this server's pages: " + path).getBytes(StandardCharsets.UTF_8), null, false);
                return;
            }

            // The hash is the strongest validator there is, so a reload of an unchanged
            // page costs a round trip to loopback and nothing else.
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.contains(asset.sha256())) {
                exchange.getResponseHeaders().add("ETag", "\"" + asset.sha256() + "\"");
                exchange.sendResponseHeaders(304, -1);
                return;
            }

            byte[] bytes = WebGUIAssetCache.fetch(path);
            if (bytes == null) {
                respond(exchange, 504, "text/plain; charset=utf-8",
                        ("The server did not send " + path).getBytes(StandardCharsets.UTF_8), null, false);
                return;
            }

            if (asset.contentType().startsWith("text/html")) {
                // No ETag on HTML: the bridge woven in below carries a per-session value,
                // so a 304 could hand back a document pointing at a dead session.
                respond(exchange, 200, asset.contentType(), withBridge(bytes), null, "HEAD".equals(method));
                return;
            }
            respond(exchange, 200, asset.contentType(), bytes, asset.sha256(), "HEAD".equals(method));
        } catch (Exception e) {
            // Warn, not debug. This catch once swallowed the only evidence that bundled
            // pages were failing outright on one loader, and the symptom above it was a
            // blank screen with nothing in the log to explain it.
            WebGUIMod.LOGGER.warn("webgui: page request failed for {}: {}",
                    exchange.getRequestURI(), e.toString());
        }
    }

    /**
     * Logs a miss the first time it is seen, and not again.
     *
     * A page that gets one path wrong usually gets thirty wrong the same way, and a wall
     * of identical warnings is how the one line that matters gets lost.
     */
    private static void warnOnce(String key, String message, Object... args) {
        // Bounded, because the keys come from whatever a page chose to request.
        if (WARNED.size() < 200 && WARNED.add(key)) {
            WebGUIMod.LOGGER.warn(message, args);
        }
    }

    private static String referrerOf(HttpExchange exchange) {
        String referrer = exchange.getRequestHeaders().getFirst("Referer");
        return referrer == null || referrer.isBlank() ? "(no referrer)" : referrer;
    }

    /**
     * Weaves the game bridge into a page before its own scripts can run.
     *
     * The mod also injects the bridge through CEF when a page starts loading, but that
     * is a message to another process and the document's scripts routinely win the race
     * — measurably so: the same page failed or succeeded depending on how long its
     * subresources took to arrive. Serving these files ourselves is what makes a
     * guarantee possible, so a page here can call {@code window.webgui} from a plain
     * inline script and simply have it work.
     *
     * Inserted at the end of {@code <head>}: late enough to leave the charset
     * declaration in the first bytes where the parser looks for it, early enough to
     * precede anything in the body.
     */
    private static byte[] withBridge(byte[] html) {
        String script = "<script>" + WebviewScriptInject.bridgeSetup(base(), WebSession.mode() == WebSession.Mode.HUD_OVERLAY) + "</script>";
        String document = new String(html, StandardCharsets.UTF_8);

        int head = indexOfIgnoreCase(document, "</head>");
        if (head >= 0) {
            return (document.substring(0, head) + script + document.substring(head))
                    .getBytes(StandardCharsets.UTF_8);
        }
        // No head to speak of. Before the first script is still before anything that
        // could ask for the bridge; failing that, the top of the document.
        int firstScript = indexOfIgnoreCase(document, "<script");
        int at = firstScript >= 0 ? firstScript : 0;
        return (document.substring(0, at) + script + document.substring(at))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(java.util.Locale.ROOT).indexOf(needle);
    }

    private static void respond(HttpExchange exchange, int status, String contentType,
                                byte[] body, String etag, boolean headOnly) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        // Revalidate rather than cache: the path is stable while its content is not, and
        // an operator editing a file expects a reload to show it.
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        if (etag != null) {
            exchange.getResponseHeaders().add("ETag", "\"" + etag + "\"");
        }
        // These files go to every player on the server, so there is nothing here that
        // reading from another origin could leak - and without this, a page hosted the
        // ordinary way could not pull an image out of the bundled set.
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (headOnly) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
