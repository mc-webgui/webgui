package space.webgui.wmc.core;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.cef.browser.OffscreenBrowser;
import space.webgui.wmc.api.ConsoleLevel;
import space.webgui.wmc.api.Damage;
import space.webgui.wmc.api.Download;
import space.webgui.wmc.api.RuntimeInfo;
import space.webgui.wmc.api.RuntimeListener;
import space.webgui.wmc.api.RuntimeState;
import space.webgui.wmc.api.SchemeHandler;
import space.webgui.wmc.api.WebRuntime;
import space.webgui.wmc.api.WebView;
import space.webgui.wmc.api.WebViewSpec;

/**
 * The runtime a mod actually gets when wmc is installed.
 *
 * <p>Found by {@code ServiceLoader}, so a mod that compiles against the api module alone picks
 * this up at runtime without naming it, and gets a runtime reporting
 * {@link RuntimeState#UNAVAILABLE} when it is absent. Nothing in the api module mentions this
 * class, and nothing here appears in the api.
 *
 * <p><strong>Starting is lazy and happens once.</strong> Constructing this downloads nothing
 * and starts nothing: the first call to {@link #whenReady()} or {@link #open} is what begins
 * it. That is not laziness for its own sake — Chromium requires custom schemes to be
 * registered before its first process comes up, so there has to be a moment between "the
 * runtime exists" and "the runtime has started" for a mod to register them in. The sequence in
 * the README depends on it:
 *
 * <pre>{@code
 * runtime.registerScheme("app", ...);
 * runtime.whenReady().thenCompose(...)
 * }</pre>
 */
public final class CefRuntime implements WebRuntime {

    private static volatile RuntimeSettings settings = RuntimeSettings.defaults();

    private final CopyOnWriteArrayList<RuntimeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, SchemeHandler> schemes = new LinkedHashMap<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final AtomicBoolean starting = new AtomicBoolean();

    private volatile RuntimeState state = RuntimeState.IDLE;
    private volatile CefHost host;
    private volatile Path installed;

    /** For {@code ServiceLoader}, which is the only thing that should call it. */
    public CefRuntime() {
    }

    /**
     * Applies what the person running the game asked for, before anything starts.
     *
     * <p>Static because the runtime is found by {@code ServiceLoader} and nobody constructs it:
     * whoever knows the settings is not who creates the object. Calling it after the runtime
     * has started changes nothing, because Chromium reads most of this once, when it starts.
     */
    public static void configure(RuntimeSettings runtimeSettings) {
        settings = Objects.requireNonNull(runtimeSettings, "runtimeSettings");
        // The one part of this a running browser can still be told. Everything else Chromium
        // read when it started; where a download goes it asks about each time, so a settings
        // screen changed mid-game means something rather than nothing.
        if (WebRuntime.get() instanceof CefRuntime runtime) {
            CefHost started = runtime.host;
            if (started != null) {
                started.downloads(settings.downloadDirectory(),
                        settings.downloads().askWhereToPut());
            }
        }
    }

    /** What the runtime is configured with. */
    public static RuntimeSettings settings() {
        return settings;
    }

    @Override
    public RuntimeState state() {
        return state;
    }

    @Override
    public CompletableFuture<Void> whenReady() {
        startOnce();
        return ready;
    }

    @Override
    public CompletableFuture<WebView> open(WebViewSpec spec) {
        Objects.requireNonNull(spec, "spec");
        startOnce();
        return ready.thenApply(ignored -> openNow(spec));
    }

    private WebView openNow(WebViewSpec spec) {
        RuntimeSettings current = settings;
        CefFrame frame = new CefFrame();
        CefWebView[] view = new CefWebView[1];
        OffscreenBrowser browser = host.open(spec.url(), spec.width(), spec.height(), spec.frameRate(),
                spec.isTransparent(),
                new OffscreenBrowser.PaintSink() {
                    @Override
                    public void onPaint(boolean popup, java.awt.Rectangle[] dirty,
                            java.nio.ByteBuffer pixels, int paintedWidth, int paintedHeight) {
                        // Two surfaces arrive here and only one of them is the page. The other
                        // is the menu a select box opens, which belongs over the page rather
                        // than in it; the frame keeps them apart and lays one over the other
                        // when something reads it.
                        if (popup) {
                            frame.acceptPopup(pixels, paintedWidth, paintedHeight);
                        } else {
                            view[0].onPaint(pixels, cover(dirty), paintedWidth, paintedHeight);
                        }
                    }

                    @Override
                    public void onPopup(boolean visible, java.awt.Rectangle rect) {
                        frame.placePopup(visible, rect.x, rect.y, rect.width, rect.height);
                    }
                });
        view[0] = new CefWebView(browser, frame, spec.width(), spec.height(),
                current.remoteDebuggingPort(), () -> {
                });
        // What a mod's listeners hear. Chromium reports to the client, which is shared by every
        // view, so the runtime is the only place that knows which page a report belongs to.
        browser.events(new OffscreenBrowser.EventSink() {
            @Override
            public void onLoadStart(String url) {
                view[0].notifyLoadStart(url);
            }

            @Override
            public void onLoadEnd(String url, int httpStatus) {
                view[0].notifyLoadEnd(url, httpStatus);
            }

            @Override
            public void onLoadFailed(String url, int errorCode, String description) {
                view[0].notifyLoadFailed(url, errorCode, description);
            }

            @Override
            public void onTitle(String title) {
                view[0].notifyTitle(title);
            }

            @Override
            public void onConsole(String severity, String message, String source, int line) {
                view[0].notifyConsole(consoleLevel(severity), message, source, line);
            }

            @Override
            public void onCursor(int cursorType) {
                view[0].notifyCursor(CefCursors.cssName(cursorType));
            }

            @Override
            public void onDownload(int id, String url, String name, String path,
                    long received, long total, boolean done, boolean canceled) {
                view[0].notifyDownload(new Download(id, url == null ? "" : url,
                        name == null ? "" : name,
                        path == null || path.isBlank() ? null : java.nio.file.Path.of(path),
                        received, total, done, canceled));
            }
        });
        if (current.openDevtoolsForEveryView() && current.remoteDebuggingPort() != 0) {
            openDevtoolsWhenListed(browser, current);
        }
        return view[0];
    }

    /**
     * Chromium's log severity as the API's console level.
     *
     * <p>Matched on the name rather than the position in the enum: an enum from another project
     * that gains a value in the middle would otherwise turn every message one level quieter,
     * and nothing would say so.
     */
    private static ConsoleLevel consoleLevel(String severity) {
        String name = severity == null ? "" : severity;
        if (name.endsWith("VERBOSE") || name.endsWith("DEBUG")) {
            return ConsoleLevel.DEBUG;
        }
        if (name.endsWith("WARNING")) {
            return ConsoleLevel.WARNING;
        }
        if (name.endsWith("ERROR") || name.endsWith("FATAL")) {
            return ConsoleLevel.ERROR;
        }
        return ConsoleLevel.INFO;
    }

    /**
     * Opens the developer tools for a view that has just been created.
     *
     * <p>Not immediately: Chromium publishes a page a moment after the browser exists, and
     * asking too early gets an empty list. Tried a few times on a thread of its own, because
     * the alternative is making whoever opened a view wait for a debugger to be ready.
     */
    private void openDevtoolsWhenListed(OffscreenBrowser browser, RuntimeSettings current) {
        Thread opener = new Thread(() -> {
            for (int attempt = 0; attempt < 40; attempt++) {
                try {
                    String url = CefDevtools.urlFor(browser, current.remoteDebuggingPort())
                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
                    current.urlOpener().accept(url);
                    return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception notYet) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "wmc-devtools-open");
        opener.setDaemon(true);
        opener.start();
    }

    @Override
    public void registerScheme(String scheme, SchemeHandler handler) {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(handler, "handler");
        synchronized (schemes) {
            if (state != RuntimeState.IDLE) {
                throw new IllegalStateException("the runtime has already started, and Chromium requires"
                        + " custom schemes to be registered before it does; register " + scheme
                        + " before the first call to whenReady or open");
            }
            schemes.put(scheme, handler);
        }
        throw new UnsupportedOperationException(
                "custom schemes are not served yet; see the roadmap in the README");
    }

    /**
     * The CEF client, or {@code null} before the runtime has started one.
     *
     * <p>See {@link CefHost#client()} for what this is for and why it is here. A caller that
     * finds {@code null} is early, not wrong: the client exists once {@link #whenReady} has
     * completed.
     */
    public org.cef.CefClient client() {
        CefHost running = host;
        return running == null ? null : running.client();
    }

    @Override
    public RuntimeInfo info() {
        Path where = installed;
        return new RuntimeInfo(NativeBundles.CEF_VERSION, chromiumVersion(),
                space.webgui.wmc.api.Platform.current(), where == null ? "" : where.toString());
    }

    private static String chromiumVersion() {
        // Carried in the build identifier rather than asked of Chromium, so that it can be
        // reported in a log or a bug report before anything has started.
        int marker = NativeBundles.JCEF_VERSION.indexOf("chromium-");
        return marker < 0 ? "unknown" : NativeBundles.JCEF_VERSION.substring(marker + "chromium-".length());
    }

    @Override
    public void addListener(RuntimeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(RuntimeListener listener) {
        listeners.remove(listener);
    }

    private void startOnce() {
        if (!starting.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(this::start, "wmc-runtime-start");
        worker.setDaemon(true);
        worker.start();
    }

    private void start() {
        try {
            RuntimeSettings current = settings;
            NativeArtifact artifact = NativeBundles.forThisMachine();
            RuntimePaths paths = RuntimePaths.standard(current.gameDirectory());
            NativeInstaller installer = new NativeInstaller(paths, new MavenNativeSource(NativeBundles.JCEF_VERSION));

            // Only said to be downloading when there is something to download. A state that
            // flickers through DOWNLOADING on every launch would have every mod drawing a
            // progress bar that is never seen.
            if (!installer.isInstalled(artifact)) {
                moveTo(RuntimeState.DOWNLOADING);
            }
            installed = installer.install(artifact, fanOut());

            moveTo(RuntimeState.STARTING);
            CefHost started = CefHost.start(BundleLayout.discover(installed), paths.cacheRoot(),
                    CefHost.HostOptions.from(current));
            started.whenReady().thenRun(() -> {
                host = started;
                moveTo(RuntimeState.READY);
                ready.complete(null);
            }).exceptionally(failure -> {
                fail(failure);
                return null;
            });
        } catch (RuntimeException | Error e) {
            fail(e);
        }
    }

    private void fail(Throwable cause) {
        moveTo(RuntimeState.FAILED);
        listeners.forEach(listener -> listener.onFailure(cause));
        ready.completeExceptionally(cause);
    }

    private void moveTo(RuntimeState next) {
        state = next;
        listeners.forEach(listener -> listener.onState(next));
    }

    private RuntimeListener fanOut() {
        return new RuntimeListener() {
            @Override
            public void onDownload(long received, long total) {
                listeners.forEach(listener -> listener.onDownload(received, total));
            }
        };
    }

    /**
     * What Chromium changed, as one rectangle, or {@code null} for "it did not say".
     *
     * <p>The only place the runtime sees Chromium's geometry; past here it is the API's own
     * {@link Damage}, and the frame is what remembers it.
     */
    private static Damage cover(java.awt.Rectangle[] dirty) {
        if (dirty == null || dirty.length == 0) {
            return null;
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (java.awt.Rectangle rectangle : dirty) {
            if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0) {
                continue;
            }
            left = Math.min(left, rectangle.x);
            top = Math.min(top, rectangle.y);
            right = Math.max(right, rectangle.x + rectangle.width);
            bottom = Math.max(bottom, rectangle.y + rectangle.height);
        }
        if (right <= left || bottom <= top) {
            return Damage.NONE;
        }
        return new Damage(left, top, right - left, bottom - top);
    }

    /**
     * Turns the browser's message loop once, and takes over setting its pace.
     *
     * <p>Not part of the API, for the same reason {@link #shutdown()} is not: only the module
     * that owns the game's frame has any business calling it, and a mod that never does still
     * gets a browser that works — the runtime turns the loop itself until something better
     * comes along. Costs a field read while no browser is running.
     */
    public void pump() {
        CefHost running = host;
        if (running != null) {
            running.pump();
        }
    }

    /** Shuts Chromium down. Not part of the API: a game ends this by ending. */
    public void shutdown() {
        CefHost running = host;
        if (running != null) {
            running.close();
        }
    }
}
