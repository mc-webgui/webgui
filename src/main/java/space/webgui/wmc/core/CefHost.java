package space.webgui.wmc.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.cef.CefApp;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.OffscreenBrowser;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefDownloadHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest;

/**
 * Chromium, running, with no window and no Minecraft.
 *
 * <p>One of these per process. Chromium is a process tree with global state, and a second
 * initialisation in the same JVM does not fail cleanly — it takes the first one with it.
 *
 * <p>Starting is asynchronous by nature: Chromium spawns its subprocesses and calls back when
 * it is ready, which is why {@link #whenReady()} exists rather than a constructor that blocks
 * until it can be used.
 *
 * <p>Everything that crosses into Chromium goes through {@link #browserThread()}. java-cef
 * drives the message loop from one thread and reaching into it from another corrupts the
 * references the native side holds, which shows up far away from the call that did it. Callers
 * do not have to know that: the methods here and on the browser marshal for them.
 */
public final class CefHost implements AutoCloseable {

    private static final int SHUTDOWN_SECONDS = 10;
    private static final int STARTUP_SECONDS = 30;

    /**
     * The thread Chromium is driven from.
     *
     * <p>It is the AWT event thread, not by choice: java-cef posts its own message-loop work
     * there, so that is where the loop runs and therefore the only thread that may make calls
     * into it. On macOS it is also the AppKit thread, which is where CEF wants to be anyway.
     */
    private static final Executor BROWSER_THREAD = work -> {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        } else {
            SwingUtilities.invokeLater(work);
        }
    };

    private static CefHost running;

    /**
     * The one place the message loop is pumped from.
     *
     * <p>java-cef's own {@code CefApp.doMessageLoopWork} checks the app state when a pump is
     * scheduled rather than when it runs, so a pump landing between the client going away and
     * CefShutdown crashes inside libcef. Chromium asks for pumps constantly during shutdown,
     * so that window is hit often. Here every pump re-checks on the event thread, at the
     * moment it would run. The native call is reached by reflection; nothing else in the
     * process pumps.
     *
     * <p>Pace comes from whoever calls {@link CefHost#pump()}: a game turning the loop once a
     * frame gets a page that answers at its frame rate, and Chromium's own requests are not
     * armed while such a driver is at work. A runtime nobody drives turns the loop itself, and
     * a driver that stops is noticed within {@link #LAPSE_MILLIS}.
     *
     * <p>Static, like the app handler that feeds it: java-cef keeps one per process.
     */
    private static final class Pump {

        /** How often the loop turns when nothing outside is turning it. */
        private static final int IDLE_MILLIS = 1000 / 30;

        /**
         * How long after its last turn a driver is still believed in.
         *
         * <p>Long enough that a frame taking several times its budget does not read as a driver
         * that has gone, short enough that a page does not appear frozen to someone who has
         * just closed the screen that was driving it.
         */
        private static final int LAPSE_MILLIS = 250;

        private static final java.lang.reflect.Method DO_WORK = findDoWork();

        private static javax.swing.Timer pending;
        private static volatile boolean suppressed;

        /** When {@link CefHost#pump()} was last called, or 0 for never. */
        private static volatile long lastDriven;

        private Pump() {
        }

        private static java.lang.reflect.Method findDoWork() {
            try {
                java.lang.reflect.Method method = CefApp.class.getDeclaredMethod("N_DoMessageLoopWork");
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException upstreamChanged) {
                return null;
            }
        }

        /** Chromium's request: pump now, or that much later. Any thread; never re-entrant. */
        static void schedule(long delayMillis) {
            if (isDriven()) {
                return;
            }
            post(delayMillis);
        }

        /** A turn asked for from outside, which is also what makes this the driven mode. */
        static void drive() {
            lastDriven = System.nanoTime();
            post(0);
        }

        private static void post(long delayMillis) {
            // Posted even from the event thread. A request that arrives while a pump is
            // running arrives from inside N_DoMessageLoopWork, and pumping again from there
            // would be re-entering CEF's loop from itself.
            SwingUtilities.invokeLater(() -> {
                cancel();
                if (suppressed) {
                    return;
                }
                if (delayMillis <= 0) {
                    pumpNow();
                    return;
                }
                arm((int) Math.min(delayMillis, IDLE_MILLIS));
            });
        }

        private static void arm(int delayMillis) {
            pending = new javax.swing.Timer(delayMillis, event -> {
                pending = null;
                pumpNow();
            });
            pending.setRepeats(false);
            pending.start();
        }

        private static boolean isDriven() {
            long last = lastDriven;
            return last != 0
                    && System.nanoTime() - last < TimeUnit.MILLISECONDS.toNanos(LAPSE_MILLIS);
        }

        /** Stops answering requests, and drops the one already waiting. Event thread. */
        static void stop() {
            suppressed = true;
            cancel();
        }

        /** Starts answering again, for a runtime started after another was shut down. */
        static void reset() {
            suppressed = false;
            lastDriven = 0;
        }

        /** Pumps if pumping is still wanted, then arranges the next turn. Event thread. */
        private static void pumpNow() {
            if (suppressed) {
                return;
            }
            work();
            // Driven, this is the watch on the driver rather than the pace: it turns the loop
            // a few times a second whatever happens, so a driver that stops - a screen closed,
            // a game paused by its window manager - is taken over from rather than leaving the
            // page stopped, and every turn it takes puts the watch off again.
            arm(isDriven() ? LAPSE_MILLIS : IDLE_MILLIS);
        }

        /**
         * One turn of Chromium's loop, unconditionally. Event thread only.
         *
         * @return whether Chromium was in a state to be pumped at all
         */
        static boolean work() {
            CefApp.CefAppState state = CefApp.getState();
            if (DO_WORK == null || state == CefApp.CefAppState.NONE || state == CefApp.CefAppState.NEW
                    || state == CefApp.CefAppState.TERMINATED) {
                return false;
            }
            try {
                DO_WORK.invoke(CefApp.getInstance());
                return true;
            } catch (ReflectiveOperationException | RuntimeException upstreamChanged) {
                return false;
            }
        }

        private static void cancel() {
            if (pending != null) {
                pending.stop();
                pending = null;
            }
        }
    }

    private final CefApp app;
    private final CefClient client;
    private final CompletableFuture<Void> contextReady;

    /**
     * The browsers Chromium has made and not yet finished closing.
     *
     * <p>Shutdown waits on this. A browser closes over several turns of the message loop and
     * reports through the life span handler when it is gone; until every one has, the loop
     * has to keep running, and once every one has, it must not run again. See {@link #close}.
     */
    private final Set<CefBrowser> live = ConcurrentHashMap.newKeySet();

    private final HostOptions options;

    /**
     * Where downloads go and whether the person is asked, which can change while the game runs.
     *
     * <p>Read when a download starts rather than taken once at start-up: this is a switch in a
     * settings screen, and a switch that needs the game restarted to mean anything is a switch
     * people conclude is broken. Everything else Chromium is told at start-up genuinely cannot
     * be told again; this can.
     */
    private volatile Path downloadDirectory;
    private volatile boolean askWhereToDownload;

    private volatile boolean closed;

    private CefHost(CefApp app, CefClient client, CompletableFuture<Void> contextReady,
            HostOptions options) {
        this.options = options;
        this.downloadDirectory = options.downloadDirectory();
        this.askWhereToDownload = options.askWhereToDownload();
        this.app = app;
        this.client = client;
        this.contextReady = contextReady;
        // The one report that a browser exists. Creation is scheduled on the browser thread
        // and Chromium answers here, which is what anything reaching for the native side has
        // to wait for; without it the wait becomes a guess about how fast this machine is.
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public void onAfterCreated(CefBrowser browser) {
                if (browser instanceof OffscreenBrowser offscreen) {
                    live.add(offscreen);
                    offscreen.markCreated();
                }
            }

            @Override
            public void onBeforeClose(CefBrowser browser) {
                live.remove(browser);
            }
        });
        // The other half of "the view is ready": the first navigation having finished. Failure
        // counts as finished, so that a page which did not load reports what went wrong rather
        // than leaving whoever asked waiting for a load that is not coming.
        //
        // These handlers are also the only place a mod's listeners can be told anything.
        // Chromium reports per client, and one client serves every view, so the report is
        // turned back into something about one page here and handed to that page's sink.
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser browser, org.cef.browser.CefFrame frame,
                    CefRequest.TransitionType transition) {
                OffscreenBrowser page = mainFrameOf(browser, frame);
                if (page != null) {
                    page.events().onLoadStart(frame.getURL());
                }
            }

            @Override
            public void onLoadEnd(CefBrowser browser, org.cef.browser.CefFrame frame, int status) {
                OffscreenBrowser page = mainFrameOf(browser, frame);
                if (page != null) {
                    page.markFirstLoadDone();
                    page.events().onLoadEnd(frame.getURL(), status);
                }
            }

            @Override
            public void onLoadError(CefBrowser browser, org.cef.browser.CefFrame frame,
                    CefLoadHandler.ErrorCode errorCode, String errorText, String failedUrl) {
                OffscreenBrowser page = mainFrameOf(browser, frame);
                if (page != null) {
                    page.markFirstLoadDone();
                    page.events().onLoadFailed(failedUrl, errorCode == null ? 0 : errorCode.getCode(),
                            errorText);
                }
            }
        });
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                if (browser instanceof OffscreenBrowser page) {
                    page.events().onTitle(title == null ? "" : title);
                }
            }

            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                    String message, String source, int line) {
                if (browser instanceof OffscreenBrowser page) {
                    page.events().onConsole(level == null ? "" : level.name(), message, source, line);
                }
                // False: Chromium still writes it to its own log, where someone chasing a
                // start-up problem with a verbose log severity expects to find it.
                return false;
            }
        });
        client.addDownloadHandler(new CefDownloadHandlerAdapter() {
            /**
             * Where the file goes, decided before a byte is written.
             *
             * <p>Somewhere in the game's own folder by default, rather than the player's
             * Downloads: a page in a game saving into the place a person keeps their own files
             * is a surprise, and two instances of the game would write over each other there.
             *
             * <p>Asked for instead, Chromium opens the system's own save dialog - the same one
             * a file input opens, which works in a game with no window of ours involved.
             */
            @Override
            public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem item,
                    String suggestedName, CefBeforeDownloadCallback callback) {
                Path directory = downloadDirectory;
                String name = safeName(suggestedName, item);
                try {
                    Files.createDirectories(directory);
                } catch (Exception cannot) {
                    // Chromium is told where anyway: it reports the failure through the item,
                    // which reaches the mod, rather than this deciding to cancel on its behalf.
                    System.err.println("wmc: could not make " + directory + " (" + cannot + ")");
                }
                // Absolute: Chromium does not resolve a relative path against this process's
                // working directory, and a download given one quietly goes nowhere.
                callback.Continue(directory.toAbsolutePath().normalize().resolve(name).toString(),
                        askWhereToDownload);
                return true;
            }

            @Override
            public void onDownloadUpdated(CefBrowser browser, CefDownloadItem item,
                    CefDownloadItemCallback callback) {
                if (browser instanceof OffscreenBrowser page) {
                    page.events().onDownload(item.getId(), item.getURL(), item.getSuggestedFileName(),
                            item.getFullPath(), item.getReceivedBytes(), item.getTotalBytes(),
                            item.isComplete(), item.isCanceled());
                }
            }
        });
    }

    /**
     * A file name that is a file name.
     *
     * <p>What a server suggests is not to be trusted with a path: a name carrying separators or
     * dots would write outside the directory this just chose. Everything but the last part is
     * dropped and the separators go with it.
     */
    private static String safeName(String suggested, CefDownloadItem item) {
        String name = suggested == null || suggested.isBlank() ? item.getSuggestedFileName() : suggested;
        if (name == null || name.isBlank()) {
            name = "download";
        }
        name = name.replace('\\', '/');
        int lastPart = name.lastIndexOf('/');
        if (lastPart >= 0) {
            name = name.substring(lastPart + 1);
        }
        name = name.replaceAll("[\\x00-\\x1f<>:\"|?*]", "_").strip();
        return name.isBlank() || name.equals(".") || name.equals("..") ? "download" : name;
    }

    /**
     * The browser a report is about, or null when it is about a frame inside the page.
     *
     * <p>A page has frames, and an advert in an iframe finishing its load is not the page
     * finishing its load. Only the main frame is the page.
     */
    private static OffscreenBrowser mainFrameOf(CefBrowser browser, org.cef.browser.CefFrame frame) {
        return frame != null && frame.isMain() && browser instanceof OffscreenBrowser page ? page : null;
    }

    /**
     * Starts Chromium from an installed bundle.
     *
     * @param layout the bundle, as {@link BundleLayout#discover} found it
     * @param cache  where this game instance keeps its browser cache; <strong>never</strong>
     *               shared with another running instance, because Chromium locks it and the
     *               loser of that race gets no browser at all
     */
    public static synchronized CefHost start(BundleLayout layout, Path cache, HostOptions options) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(options, "options");
        if (running != null && !running.closed) {
            throw new IllegalStateException("Chromium is already running in this process");
        }

        CefLoader.load(layout);
        String[] arguments = arguments(layout, options);
        if (!CefApp.startup(arguments)) {
            throw new InstallException("Chromium refused to start from " + layout.root());
        }

        CefSettings settings = settings(layout, cache, options);
        // The future exists before Chromium does. Initialisation calls back from inside
        // getInstance, so anything built afterwards is too late to be told about it.
        CompletableFuture<Void> contextReady = new CompletableFuture<>();
        Pump.reset();
        CefApp.addAppHandler(new CefAppHandlerAdapter(arguments) {
            @Override
            public void onContextInitialized() {
                contextReady.complete(null);
            }

            /**
             * Chromium asking for the loop to be pumped. This is the only way java-cef's
             * native side ever asks - the library's binary names no other Java method for it -
             * so answering it here, and not with the adapter's default, is what keeps
             * java-cef's own timer from ever being made. See {@link Pump}.
             */
            @Override
            public void onScheduleMessagePumpWork(long delayMillis) {
                Pump.schedule(delayMillis);
            }

            @Override
            public void stateHasChanged(CefApp.CefAppState state) {
                if (state == CefApp.CefAppState.INITIALIZATION_FAILED) {
                    // The bundle is named because it is where the failure could be, but it is
                    // rarely where it is: by this point the runtime has been installed and
                    // checked. Far more often another game is still running and holding the
                    // cache, and a message about the bundle sends the search the wrong way.
                    contextReady.completeExceptionally(new InstallException(
                            "Chromium failed to initialise from " + layout.root()
                                    + "; if the runtime installed cleanly, the usual cause is"
                                    + " another instance still holding the browser cache at "
                                    + cache));
                }
            }
        });

        // Built on the browser thread like everything else that touches Chromium. Waited for
        // rather than returned as a future because a caller holding a half-started runtime
        // has nothing useful to do with it, and the wait is bounded: this creates objects, it
        // does not start Chromium, which reports for itself through whenReady.
        CompletableFuture<CefHost> built = new CompletableFuture<>();
        BROWSER_THREAD.execute(() -> {
            try {
                CefApp app = CefApp.getInstance(arguments, settings);
                built.complete(new CefHost(app, app.createClient(), contextReady, options));
            } catch (RuntimeException | Error e) {
                built.completeExceptionally(e);
            }
        });
        try {
            running = built.get(STARTUP_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InstallException("interrupted while starting Chromium", interrupted);
        } catch (Exception e) {
            throw new InstallException("could not start Chromium from " + layout.root(), e);
        }
        return running;
    }

    private static CefSettings settings(BundleLayout layout, Path cache, HostOptions options) {
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.browser_subprocess_path = layout.helper().toString();
        settings.resources_dir_path = layout.resourcesDirectory().toString();
        layout.localesDirectory().ifPresent(locales -> settings.locales_dir_path = locales.toString());
        settings.cache_path = cache.toString();
        settings.persist_session_cookies = true;
        settings.log_severity = options.logSeverity();
        settings.remote_debugging_port = options.remoteDebuggingPort();
        settings.windowless_rendering_enabled = true;
        // Transparent, so a page that does not paint a background shows what is behind it in
        // the game rather than a white rectangle. A page that wants white can say so in CSS.
        settings.background_color = settings.new ColorType(0, 0, 0, 0);
        try {
            Files.createDirectories(cache);
        } catch (Exception e) {
            throw new InstallException("could not create the browser cache directory at " + cache, e);
        }
        return settings;
    }

    private static String[] arguments(BundleLayout layout, HostOptions options) {
        List<String> arguments = new ArrayList<>();
        // macOS needs to be told where everything is, and the three of these go together.
        //
        // Without the framework path, java-cef looks for the framework beside whatever the
        // library path property happens to be — a list of directories — and fails with a path
        // that has the whole list in it.
        //
        // The main bundle is the helper application, which is not a mistake. Chromium works
        // out where the framework and its graphics libraries live relative to the main bundle,
        // and a bundle laid out flat has no outer application to point at. Naming the helper
        // gives it a real bundle to reason from; naming the directory leaves the subprocesses
        // looking for the graphics libraries beside their own executable.
        layout.frameworkDirectory().ifPresent(framework -> {
            arguments.add("--framework-dir-path=" + framework);
            layout.helperBundle().ifPresent(bundle -> arguments.add("--main-bundle-path=" + bundle));
            arguments.add("--browser-subprocess-path=" + layout.helper());
        });
        arguments.add("--off-screen-rendering-enabled");
        // Off-screen rendering through the GPU hands back a shared texture rather than
        // pixels, and the pixels are the entire point here. The page still composites and
        // animates; it is the final surface that comes back as memory.
        arguments.add("--disable-gpu");
        arguments.add("--disable-gpu-compositing");
        // A page in a game screen is not a tab the player chose to open, so a video or a
        // sound that starts on its own is a surprise. Left to Chromium's default, which
        // requires a gesture, unless the mod asks otherwise.
        if (options.autoplay()) {
            arguments.add("--autoplay-policy=no-user-gesture-required");
        }
        if (options.remoteDebuggingPort() != 0) {
            arguments.add("--remote-allow-origins=" + allowedDebuggerOrigins(options.remoteDebuggingPort()));
        }
        arguments.addAll(options.extraArguments());
        return arguments.toArray(String[]::new);
    }

    /**
     * Who is allowed to open a debugging connection, and deliberately not everyone.
     *
     * <p>Since Chromium 111 the debugging endpoint refuses a websocket whose {@code Origin} is
     * not named here, and nothing is named by default. Without this the developer tools load in
     * a browser, show an empty inspector and report the connection as failed, which reads as a
     * port that is not open rather than as a request that was turned away.
     *
     * <p>Named rather than {@code *}, which is what every answer on the internet says to pass.
     * That check is the only thing standing between this port and any web page the person
     * happens to have open: with it off, a site in another tab can connect to the port and
     * drive every page the game has open. So the tools are let in — Chromium's own frontend,
     * and the one this port serves itself — and the open web is not.
     */
    private static String allowedDebuggerOrigins(int port) {
        return "devtools://devtools,http://127.0.0.1:" + port + ",http://localhost:" + port;
    }

    /** Completes once Chromium is initialised and browsers can be opened. */
    public CompletableFuture<CefHost> whenReady() {
        return contextReady.thenApply(ignored -> this);
    }

    /**
     * Opens a page.
     *
     * @param transparent whether what the page does not paint is left see-through, for a page
     *                    drawn over the world rather than over a screen of its own
     * @param sink        receives every frame Chromium paints, on a Chromium thread
     */
    public OffscreenBrowser open(String url, int width, int height, int frameRate,
            boolean transparent, OffscreenBrowser.PaintSink sink) {
        if (closed) {
            throw new IllegalStateException("the browser runtime has been shut down");
        }
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = frameRate;
        // The flag goes to Chromium when the browser is made; there is no per-browser
        // background colour to set here, java-cef's browser settings carry only the frame
        // rate. The colour behind every page is the runtime's, and it is fully transparent.
        OffscreenBrowser browser = new OffscreenBrowser(
                client, url, transparent, null, settings, width, height, BROWSER_THREAD, sink);
        // Returns before Chromium has made it: creation is scheduled on the browser thread,
        // and until it has run the object is a handle to a browser that is on its way.
        browser.createImmediately();
        return browser;
    }

    /**
     * The thread everything to do with the browser happens on.
     *
     * <p>Public because the Minecraft side needs it for the same reason this class does.
     */
    public Executor browserThread() {
        return BROWSER_THREAD;
    }

    /**
     * Changes where downloads go, and whether the person is asked, while the game runs.
     *
     * <p>Apart from everything else the runtime is told at start-up, because this is the one
     * setting a person expects to take effect the moment they change it.
     */
    public void downloads(Path directory, boolean ask) {
        this.downloadDirectory = Objects.requireNonNull(directory, "directory");
        this.askWhereToDownload = ask;
    }

    /**
     * Turns Chromium's message loop, and takes over setting its pace.
     *
     * <p>Everything a browser does — finishing a load, painting, running a timer in the page —
     * happens in that loop, which turns on the event thread; see {@link Pump}. Call this once
     * a frame from the game and pages answer at the rate the game draws at; stop calling it
     * and the runtime goes back to turning the loop itself. Calling it is never required.
     */
    public void pump() {
        if (!closed) {
            Pump.drive();
        }
    }

    /** Runs the message loop until {@code until} is true, or the time runs out. */
    public boolean pumpUntil(java.util.function.BooleanSupplier until, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (until.getAsBoolean()) {
                return true;
            }
            pump();
            Thread.sleep(5);
        }
        return until.getAsBoolean();
    }

    /**
     * The CEF client every browser here is made against.
     *
     * <p>A deliberate hole in an otherwise closed wall. Chromium can do things this runtime
     * does not describe yet — a message router for the page to call the game being the one
     * that matters — and until it does, the alternative is a consumer that cannot do them.
     *
     * <p>Whatever is registered here outlives no restart: a new runtime builds a new client,
     * and handlers attached to the old one are attached to nothing.
     */
    public CefClient client() {
        return client;
    }

    /**
     * Shuts Chromium down and waits for it to finish.
     *
     * <p>Waiting is the part that matters. Disposing asks Chromium to terminate and returns
     * at once; a process that exits in that window leaves work queued against a half-torn-down
     * browser and, worse, helper processes with nothing left to parent them. The game closing
     * should take the browser with it.
     */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        // One task on the event thread, and its shape is the whole point.
        //
        // Disposing the client closes its browsers over several turns of Chromium's loop, and
        // the client reports itself gone only when the last of them has. The loop has to keep
        // turning until then - which is what CEF says too: keep calling DoMessageLoopWork until
        // the browsers are closed, then shut down. But a pump that lands after the client has
        // gone and before CefShutdown has run dereferences what is no longer there. That is
        // the SIGSEGV inside libcef that killed the test workers, seen under gdb in java-cef's
        // own pump runnable, and it landed whenever Chromium's constant requests for work
        // during teardown happened to be answered in that window.
        //
        // So the loop is turned here, synchronously, exactly until the browsers have gone, and
        // the app is disposed in the same task. With no client left it shuts down at once. The
        // pump has already been told to stop answering requests, and nothing else in the
        // process pumps - see Pump - so nothing can land in between.
        BROWSER_THREAD.execute(() -> {
            Pump.stop();
            client.dispose();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_SECONDS);
            while (!live.isEmpty() && System.nanoTime() < deadline && Pump.work()) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            app.dispose();
        });
        waitFor(() -> CefApp.getState() == CefApp.CefAppState.TERMINATED, SHUTDOWN_SECONDS);

        synchronized (CefHost.class) {
            if (running == this) {
                running = null;
            }
        }
    }

    /**
     * Waits for something to become true, from the thread shutting down.
     *
     * <p>Not pumping, on purpose: the loop runs on the event thread, and this is the thread
     * waiting for it. Bounded, so that a browser which will not close cannot keep the game
     * from quitting.
     */
    private static void waitFor(java.util.function.BooleanSupplier until, int seconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!until.getAsBoolean() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** What can be varied about how Chromium is started. */
    public record HostOptions(CefSettings.LogSeverity logSeverity, int remoteDebuggingPort,
            boolean autoplay, List<String> extraArguments,
            Path downloadDirectory, boolean askWhereToDownload) {

        public HostOptions {
            Objects.requireNonNull(logSeverity, "logSeverity");
            extraArguments = List.copyOf(extraArguments);
        }

        /** Quiet, no debugging port, no autoplay. */
        public static HostOptions defaults() {
            return new HostOptions(CefSettings.LogSeverity.LOGSEVERITY_WARNING, 0, false, List.of(),
                    Paths.get(".").resolve(".wmc").resolve("downloads"), false);
        }

        /** What the person running the game asked for. */
        public static HostOptions from(RuntimeSettings settings) {
            CefSettings.LogSeverity severity = switch (settings.logLevel()) {
                case VERBOSE -> CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
                case INFO -> CefSettings.LogSeverity.LOGSEVERITY_INFO;
                case WARNING -> CefSettings.LogSeverity.LOGSEVERITY_WARNING;
                case ERROR -> CefSettings.LogSeverity.LOGSEVERITY_ERROR;
                case OFF -> CefSettings.LogSeverity.LOGSEVERITY_DISABLE;
            };
            return new HostOptions(severity, settings.remoteDebuggingPort(),
                    settings.allowAutoplay(), settings.extraArguments(),
                    settings.downloadDirectory(), settings.downloads().askWhereToPut());
        }

        public HostOptions withRemoteDebuggingPort(int port) {
            return new HostOptions(logSeverity, port, autoplay, extraArguments,
                    downloadDirectory, askWhereToDownload);
        }

        public HostOptions withLogSeverity(CefSettings.LogSeverity severity) {
            return new HostOptions(severity, remoteDebuggingPort, autoplay, extraArguments,
                    downloadDirectory, askWhereToDownload);
        }
    }
}
