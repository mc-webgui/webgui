package space.webgui.wmc.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import org.cef.browser.OffscreenBrowser;
import space.webgui.wmc.api.Devtools;

/**
 * The developer tools for one view, two ways.
 *
 * <p>{@link #openWindow} is the short way: Chromium opens the inspector in a window of its
 * own, for this page, with nothing asked of the game but the message loop it already drives.
 * No AWT window is involved — the window is Chromium's, not the toolkit's — and no port has
 * to have been opened at start-up.
 *
 * <p>The debugging port is the other way, and still worth having: it lets a browser already
 * running on the machine attach to the page, which is a second screen with the developer's own
 * extensions and settings rather than a window belonging to the game. The port belongs to the
 * process, not to a view: Chromium takes it once, at start-up. Which is why {@link #enable}
 * can only confirm what was asked for before the browser started rather than turn it on now.
 */
final class CefDevtools implements Devtools {

    private final OffscreenBrowser browser;
    private final int port;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    CefDevtools(OffscreenBrowser browser, int port) {
        this.browser = browser;
        this.port = port;
    }

    @Override
    public CompletableFuture<Integer> enable(int wantedPort) {
        if (port == 0) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "the developer tools are off; Chromium takes its debugging port when it starts,"
                            + " so it has to be set before the runtime does"));
        }
        if (wantedPort != 0 && wantedPort != port) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "the debugging port is " + port + " and is fixed at start-up; " + wantedPort
                            + " would need the runtime to be started again"));
        }
        return CompletableFuture.completedFuture(port);
    }

    @Override
    public OptionalInt port() {
        return port == 0 ? OptionalInt.empty() : OptionalInt.of(port);
    }

    @Override
    public CompletableFuture<String> frontendUrl() {
        if (port == 0) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "the developer tools are off"));
        }
        // Asked for rather than assembled. The address of the tools for one page is issued by
        // Chromium and contains an identifier for that page; guessing it would open the tools
        // on whichever page happens to be first, which with several views open is a coin toss.
        return targets().thenApply(this::findThisBrowser);
    }

    @Override
    public CompletableFuture<String> call(String method, String paramsJson) {
        // Waited for rather than demanded. Opening a view returns before Chromium has made the
        // browser and before the page has loaded, so a mod calling this straight afterwards is
        // early, not wrong — and there is nothing it could do about it from the outside.
        // Refusing here made that a race which one platform happened to win and another lost.
        return browser.whenCreated()
                .thenCompose(ignored -> browser.whenFirstLoaded())
                .thenCompose(ignored -> {
                    var devtools = browser.getDevToolsClient();
                    if (devtools == null || devtools.isClosed()) {
                        return CompletableFuture.<String>failedFuture(new IllegalStateException(
                                "this view has no developer tools connection"));
                    }
                    return paramsJson == null || paramsJson.isBlank()
                            ? devtools.executeDevToolsMethod(method)
                            : devtools.executeDevToolsMethod(method, paramsJson);
                });
    }

    @Override
    public void onEvent(java.util.function.BiConsumer<String, String> handler) {
        java.util.Objects.requireNonNull(handler, "handler");
        // Attached on the same terms as a call: a browser that does not exist yet has no
        // connection to listen to, and a mod subscribing the moment it opens a view is early
        // rather than wrong.
        browser.whenCreated()
                .thenCompose(ignored -> browser.whenFirstLoaded())
                .thenAccept(ignored -> {
                    var devtools = browser.getDevToolsClient();
                    if (devtools != null && !devtools.isClosed()) {
                        devtools.addEventListener(handler::accept);
                    }
                });
    }

    @Override
    public void openWindow() {
        browser.openDevtoolsWindow();
    }

    @Override
    public void closeWindow() {
        browser.closeDevtoolsWindow();
    }

    @Override
    public void disable() {
        var devtools = browser.getDevToolsClient();
        if (devtools != null && !devtools.isClosed()) {
            devtools.close();
        }
    }

    private CompletableFuture<String> targets() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }

    /**
     * Picks this view out of the list Chromium publishes.
     *
     * <p>Matched on the page address, and read with string scanning rather than a JSON parser.
     * The api module deliberately has no opinion about JSON and this module has no need of one
     * for two fields; a dependency for that would be paid for by everyone who depends on this.
     */
    private String findThisBrowser(String json) {
        String url = browser.getURL();
        int best = -1;
        int searched = 0;
        while (true) {
            int entry = json.indexOf("\"devtoolsFrontendUrl\"", searched);
            if (entry < 0) {
                break;
            }
            int blockStart = json.lastIndexOf('{', entry);
            int blockEnd = json.indexOf('}', entry);
            if (blockStart < 0 || blockEnd < 0) {
                break;
            }
            String block = json.substring(blockStart, blockEnd);
            if (best < 0 || (url != null && !url.isEmpty() && block.contains(url))) {
                best = entry;
                if (url != null && !url.isEmpty() && block.contains(url)) {
                    break;
                }
            }
            searched = entry + 1;
        }
        if (best < 0) {
            throw new IllegalStateException("Chromium is not listing any page on port " + port);
        }
        return localFrontend(json, best);
    }

    /**
     * The address of the tools for that page, served by this port rather than by Google.
     *
     * <p>Chromium's own answer, {@code devtoolsFrontendUrl}, points at
     * {@code chrome-devtools-frontend.appspot.com} — the frontend hosted for the revision it
     * was built from. Opening that one loads a page whose origin is that site, and the
     * debugging endpoint then refuses its websocket: the tools appear and say "Debugging
     * connection was closed". Allowing that origin instead would mean letting a site on the
     * internet open a socket to this port.
     *
     * <p>The same frontend is inside Chromium and this port serves it. Its origin is the port
     * itself, which is allowed, and it needs no internet — which a game in the middle of
     * nowhere may not have either.
     */
    private String localFrontend(String json, int entry) {
        int blockEnd = json.indexOf('}', entry);
        int socketAt = json.lastIndexOf("\"webSocketDebuggerUrl\"", blockEnd < 0 ? json.length() : blockEnd);
        if (socketAt < 0 || socketAt < json.lastIndexOf('{', entry)) {
            socketAt = json.indexOf("\"webSocketDebuggerUrl\"", entry);
        }
        if (socketAt < 0) {
            // Nothing to connect to, so the address Chromium gave is the only one there is.
            String path = value(json, entry);
            return path.startsWith("http") ? path : "http://127.0.0.1:" + port + path;
        }
        String socket = value(json, socketAt);
        String withoutScheme = socket.startsWith("ws://") ? socket.substring("ws://".length()) : socket;
        return "http://127.0.0.1:" + port + "/devtools/inspector.html?ws=" + withoutScheme;
    }

    private static String value(String json, int keyAt) {
        int colon = json.indexOf(':', keyAt);
        int open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        if (colon < 0 || open < 0 || close < 0) {
            throw new IllegalStateException("could not read the developer tools address");
        }
        return json.substring(open + 1, close).replace("\\u002F", "/").replace("\\/", "/");
    }

    /** For the runtime, which opens the tools itself when it is configured to. */
    static CompletableFuture<String> urlFor(OffscreenBrowser browser, int port) {
        return new CefDevtools(browser, port).frontendUrl();
    }
}
