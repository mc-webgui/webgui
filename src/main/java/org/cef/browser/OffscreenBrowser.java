package org.cef.browser;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

/**
 * A browser that paints into a buffer instead of a window.
 *
 * <p><strong>Why this class is in someone else's package.</strong> java-cef renders off-screen
 * only through {@code CefBrowserOsr}, which paints into an OpenGL canvas — an AWT component,
 * which a game that owns the window and the GL context cannot host. The class that would have
 * to be extended to do otherwise, {@code CefBrowser_N}, is package-private. So the only way to
 * render into a buffer of our own is to sit in that package. Every embedder of java-cef that
 * draws into a game does the same; it is upstream's shape, not a trick.
 *
 * <p>Nothing here reaches into java-cef's internals beyond that. It extends the class, calls
 * the protected methods it is meant to call, and implements the handler interface. Should
 * upstream ever offer a supported way in, this is the one file that changes.
 *
 * <p>Everything public on this class is called by the runtime, never by a consuming mod: no
 * Chromium type appears in the API, and this is a Chromium type.
 */
public final class OffscreenBrowser extends CefBrowser_N implements CefRenderHandler {

    /** Receives finished frames. */
    @FunctionalInterface
    public interface PaintSink {
        /**
         * A frame, or part of one.
         *
         * @param popup  true for the separate surface a select box or autocomplete list draws
         *               into, which is composited over the page rather than being it
         * @param dirty  the parts that changed, in pixels
         * @param pixels BGRA, {@code width * height * 4} bytes, valid only for the duration of
         *               the call
         */
        void onPaint(boolean popup, Rectangle[] dirty, ByteBuffer pixels, int width, int height);

        /**
         * Where the popup surface is, and whether it is showing.
         *
         * <p>Sent when Chromium opens, moves or closes one. It arrives separately from the
         * pixels because the two are separate in CEF: the rectangle can change without a paint,
         * and the first paint of a popup can arrive before anything has been said about where
         * it goes.
         *
         * @param rect in page pixels, and meaningless when not visible
         */
        default void onPopup(boolean visible, Rectangle rect) {
        }
    }

    /**
     * Told what the page is doing, so that the runtime can pass it on.
     *
     * <p>Chromium reports these per client rather than per browser, and the client is shared by
     * every view. This is where the report is turned back into something about one page.
     */
    public interface EventSink {
        void onLoadStart(String url);

        void onLoadEnd(String url, int httpStatus);

        void onLoadFailed(String url, int errorCode, String description);

        void onTitle(String title);

        void onConsole(String severity, String message, String source, int line);

        /**
         * A file the page is downloading, as it goes and once more when it stops.
         *
         * @param path  where it is being written, which Chromium settles before the first of
         *              these and may be empty until it has
         * @param total bytes expected, or -1 when the server did not say
         */
        default void onDownload(int id, String url, String name, String path,
                long received, long total, boolean done, boolean canceled) {
        }

        /** The cursor the page wants, as Chromium's own cursor type. */
        default void onCursor(int cursorType) {
        }
    }

    /** Does nothing, for a browser nobody has claimed yet. */
    private static final EventSink NO_EVENTS = new EventSink() {
        @Override
        public void onLoadStart(String url) {
        }

        @Override
        public void onLoadEnd(String url, int httpStatus) {
        }

        @Override
        public void onLoadFailed(String url, int errorCode, String description) {
        }

        @Override
        public void onTitle(String title) {
        }

        @Override
        public void onConsole(String severity, String message, String source, int line) {
        }
    };

    private final CopyOnWriteArrayList<Consumer<CefPaintEvent>> paintListeners = new CopyOnWriteArrayList<>();
    private final PaintSink sink;
    private final boolean transparent;

    private volatile EventSink events = NO_EVENTS;

    /**
     * An AWT component that is never shown, never given a parent and never painted.
     *
     * <p>Only ever handed out, never used to draw with. java-cef asks a browser for its
     * component in a few places — focus handling among them — and the upstream off-screen
     * browser answers with the OpenGL canvas it renders into. There is no such canvas here,
     * and answering with nothing means those paths work on null. A bare canvas, with no peer
     * and no parent, is a Java object rather than a window and costs nothing.
     *
     * <p>Creating the browser is told there is no component, which is what upstream does too:
     * an off-screen browser has no window for Chromium to attach to.
     */
    private final Canvas canvas = new Canvas();

    /**
     * The one thread that may talk to Chromium.
     *
     * <p>Every call across this boundary is marshalled onto it, and that is not tidiness.
     * java-cef drives the message loop from a single thread, and a call made from another
     * while that loop is running corrupts the references the native side holds. The JVM says
     * so only if asked — {@code -Xcheck:jni} reports "bad global or local ref" — and otherwise
     * it surfaces as an exception with no stack trace on Windows and a process that traps on
     * macOS, in neither case anywhere near the call that did it.
     *
     * <p>Marshalling is also how the contract in the API is kept: every method safe to call
     * from any thread, and none of them blocking.
     */
    private final Executor browserThread;

    private final CompletableFuture<Void> created = new CompletableFuture<>();
    private final CompletableFuture<Void> firstLoad = new CompletableFuture<>();
    private final Object pendingLock = new Object();

    private CompletableFuture<Void> pending = created;

    /** Whether {@link #setFocus} is on the stack. Browser thread only; see the method. */
    private boolean settingFocus;


    private volatile Rectangle viewRect;
    private volatile Rectangle popupRect = new Rectangle(0, 0, 0, 0);
    private volatile boolean popupVisible;
    private volatile int cursor;

    public OffscreenBrowser(CefClient client, String url, boolean transparent,
            CefRequestContext context, CefBrowserSettings settings,
            int width, int height, Executor browserThread, PaintSink sink) {
        this(client, url, transparent, context, settings, width, height, browserThread, sink, null, null);
    }

    private OffscreenBrowser(CefClient client, String url, boolean transparent,
            CefRequestContext context, CefBrowserSettings settings,
            int width, int height, Executor browserThread, PaintSink sink,
            CefBrowser_N parent, Point inspectAt) {
        super(client, url, context, parent, inspectAt, settings);
        this.sink = Objects.requireNonNull(sink, "sink");
        this.browserThread = Objects.requireNonNull(browserThread, "browserThread");
        this.transparent = transparent;
        this.viewRect = new Rectangle(0, 0, Math.max(1, width), Math.max(1, height));
    }

    private void onBrowserThread(Runnable work) {
        browserThread.execute(work);
    }

    /**
     * Runs work on the browser thread, once there is a browser for it to reach.
     *
     * <p>Creation is asynchronous, and everything below this line is a call into a browser that
     * may not exist yet. Sent too early it arrives nowhere and is lost in silence: a page told
     * to load that never loads, a keystroke that goes missing, a view closed a moment after it
     * was opened and left running anyway. One place to wait beats twenty places to remember.
     *
     * <p>Chained rather than hung off the same future one dependent at a time, because several
     * dependents of one future are not run in the order they were added — and a key released
     * before it was pressed is a worse bug than the one being fixed. Each link also clears the
     * failure of the one before it, so that work which threw does not silently cancel every
     * call that comes after it.
     */
    private void whenCreatedOnBrowserThread(Runnable work) {
        synchronized (pendingLock) {
            pending = pending.handle((ignored, failure) -> null)
                    .thenRun(() -> onBrowserThread(work));
        }
    }

    // --- lifecycle ---------------------------------------------------------------------

    @Override
    public void createImmediately() {
        // A browser made for a parent is the developer tools for that parent, and asks to be
        // created down a different path: java-cef turns that one into Chromium's own
        // ShowDevTools, which opens the tools in a window of its own. Creating it as an
        // ordinary browser instead would quietly produce a second copy of the page.
        // No window handle: this browser has no window, which is the point. Chromium asks for
        // its size through getViewRect instead. The component is not optional though - see
        // the field.
        onBrowserThread(() ->
                createBrowser(getClient(), 0L, getUrl(), true, transparent, null, getRequestContext()));
    }

    @Override
    public Component getUIComponent() {
        return canvas;
    }

    /**
     * Something to build AWT events against.
     *
     * <p>An event needs a source component and throws without one. It is never delivered to
     * this component; it is handed straight to Chromium.
     */
    public Component eventSource() {
        return canvas;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    /**
     * Never asked for, because the developer tools are never opened that way here.
     *
     * <p>{@link #openDevtoolsWindow} explains why: java-cef opens the inspector as a pop-up of
     * Chromium's own and says so in its source. Nothing this method returned could change that,
     * and an object handed back would only be a place to look for a window that is not there.
     */
    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        throw new UnsupportedOperationException("see openDevtoolsWindow");
    }

    /**
     * Completes when Chromium has made the browser this object stands for.
     *
     * <p>{@link #createImmediately} schedules the creation and returns, so for a short while
     * this object is a handle to a browser that does not exist yet. Anything that reaches for
     * the native side — the developer tools connection above all — has to wait for this rather
     * than assume it, or it is a race that one platform happens to win and another loses.
     */
    public CompletableFuture<Void> whenCreated() {
        return created;
    }

    /** Called by the runtime's life span handler, once Chromium reports the browser made. */
    public void markCreated() {
        created.complete(null);
    }

    /**
     * Completes when the page this browser opened with has finished loading, one way or the
     * other.
     *
     * <p>A browser that exists is not yet a page that can be spoken to: the developer tools
     * answer a command sent while the first navigation is still running with "Inspected target
     * navigated or closed", which says nothing a mod could act on. Failure completes it too —
     * a page that did not load is still a page, and the error that follows should come from
     * the thing that was asked rather than from the waiting.
     */
    public CompletableFuture<Void> whenFirstLoaded() {
        return firstLoad;
    }

    /** Called by the runtime's load handler, on load end and on load failure alike. */
    public void markFirstLoadDone() {
        firstLoad.complete(null);
    }

    /** Where this browser's page events go. Set by the runtime as it hands out the view. */
    public void events(EventSink sink) {
        this.events = Objects.requireNonNull(sink, "sink");
    }

    /** What the runtime's handlers report through. */
    public EventSink events() {
        return events;
    }

    /**
     * Opens the developer tools for this page, in a window of their own.
     *
     * <p>Refuses, rather than doing nothing or opening a window that hangs. Minecraft sets
     * {@code java.awt.headless} on the way up — {@code net.minecraft.client.main.Main} does it
     * before anything else — and a headless process cannot put a window on the screen. The
     * window this makes is an AWT one, so there it cannot be made; what used to happen instead
     * was an exception on the event thread, printed to a stream nobody was reading, and a key
     * that appeared to do nothing at all.
     *
     * @throws UnsupportedOperationException always; see above
     */
    public void openDevtoolsWindow() {
        throw new UnsupportedOperationException("the developer tools cannot be opened in a window "
                + "from here. java-cef hands the inspector to Chromium's own ShowDevTools and "
                + "leaves the window information empty on purpose - its source says the tools "
                + "\"cannot be integrated into Java UI but must be opened as a pop-up\" - and "
                + "that pop-up belongs to the thread driving CEF, which runs no message loop of "
                + "its own, so it renders once and answers nothing. Use the debugging port: "
                + "settings, devtools.enabled, then devtools().frontendUrl().");
    }

    /** Closes the developer tools connection, if one was opened over the port. */
    public void closeDevtoolsWindow() {
        whenCreatedOnBrowserThread(this::closeDevTools);
    }

    // --- geometry ----------------------------------------------------------------------

    /** Resizes the page. Chromium picks the new size up on its next frame. */
    public void resize(int width, int height) {
        viewRect = new Rectangle(0, 0, Math.max(1, width), Math.max(1, height));
        whenCreatedOnBrowserThread(() -> wasResized(viewRect.width, viewRect.height));
    }

    public Rectangle viewRect() {
        return new Rectangle(viewRect);
    }

    /** The cursor Chromium last asked for, as a CEF cursor type ordinal. */
    public int cursor() {
        return cursor;
    }

    // --- input -------------------------------------------------------------------------
    //
    // The methods that take these events are protected upstream, for a browser that is an AWT
    // component and receives them itself. This one is not, so the runtime feeds them in.

    public void send(KeyEvent event) {
        whenCreatedOnBrowserThread(() -> sendKeyEvent(event));
    }

    public void send(MouseEvent event) {
        whenCreatedOnBrowserThread(() -> sendMouseEvent(event));
    }

    public void send(MouseWheelEvent event) {
        whenCreatedOnBrowserThread(() -> sendMouseWheelEvent(event));
    }

    /** Asks Chromium to repaint everything, whether or not it thinks anything changed. */
    public void repaint() {
        whenCreatedOnBrowserThread(this::invalidate);
    }

    // --- everything else that crosses into Chromium --------------------------------------
    //
    // Overridden for one reason: so that it happens on the browser thread rather than on
    // whichever thread the caller was on. The behaviour is the inherited one.

    @Override
    public void executeJavaScript(String code, String url, int line) {
        whenCreatedOnBrowserThread(() -> super.executeJavaScript(code, url, line));
    }

    @Override
    public void loadURL(String url) {
        whenCreatedOnBrowserThread(() -> super.loadURL(url));
    }

    @Override
    public void setFocus(boolean focus) {
        whenCreatedOnBrowserThread(() -> {
            // Chromium answers N_SetFocus by reporting the focus back through the client's
            // focus handler, and java-cef's client answers that by asking the browser for the
            // focus again - same value, same thread, inside this very call. Upstream's
            // off-screen browser gets away with it because its request goes to an AWT canvas
            // and comes back as a focus event on a later turn of the event loop. There is no
            // canvas here, so the circle closes synchronously and the stack overflows: a
            // StackOverflowError on the event thread with nothing left to print a trace with,
            // which java-cef describes and clears, leaving its bookkeeping half done. What a
            // later Chromium task then finds in that state is a null it does not check for.
            // Measured on Linux with -Xlog:exceptions and gdb; the same circle is behind the
            // trace-less exceptions in every log on every platform.
            //
            // So a request that arrives while one is being carried out is the echo, and is
            // dropped. Nothing independent is: the flag is held only for the duration of the
            // native call, on the one thread that ever makes it.
            if (settingFocus) {
                return;
            }
            settingFocus = true;
            try {
                super.setFocus(focus);
            } finally {
                settingFocus = false;
            }
        });
    }

    @Override
    public void setZoomLevel(double level) {
        whenCreatedOnBrowserThread(() -> super.setZoomLevel(level));
    }

    @Override
    public void reload() {
        whenCreatedOnBrowserThread(super::reload);
    }

    @Override
    public void stopLoad() {
        whenCreatedOnBrowserThread(super::stopLoad);
    }

    @Override
    public void close(boolean force) {
        whenCreatedOnBrowserThread(() -> super.close(force));
    }

    // --- screenshots -------------------------------------------------------------------

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        // Upstream reads this back out of an OpenGL texture. There is no texture here, so it
        // is the next frame Chromium paints - which is also the only thing that is certainly
        // current.
        CompletableFuture<BufferedImage> shot = new CompletableFuture<>();
        Consumer<CefPaintEvent> once = new Consumer<>() {
            @Override
            public void accept(CefPaintEvent event) {
                if (event.getPopup()) {
                    return;
                }
                removeOnPaintListener(this);
                shot.complete(toImage(event.getRenderedFrame(), event.getWidth(), event.getHeight()));
            }
        };
        addOnPaintListener(once);
        repaint();
        return shot;
    }

    /** BGRA as Chromium produces it into an image, which is the order Java calls TYPE_INT_ARGB. */
    static BufferedImage toImage(ByteBuffer pixels, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer source = pixels.duplicate();
        source.rewind();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int blue = source.get() & 0xFF;
                int green = source.get() & 0xFF;
                int red = source.get() & 0xFF;
                int alpha = source.get() & 0xFF;
                row[x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    // --- CefRenderHandler ---------------------------------------------------------------

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(viewRect);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo info) {
        // Declined on purpose, and not because there is nothing to say.
        //
        // When this returns true, java-cef reads the answer back in GetJNIScreenInfo, which
        // wraps the very same local reference in a second owning ScopedJNIObjectLocal - the
        // first belongs to GetScreenInfo, which created it. The reference is deleted twice.
        // Under -Xcheck:jni that is the fatal "Bad global or local ref passed to JNI" inside
        // RenderHandler::GetScreenInfo; without the check the second delete clears whatever
        // local reference has since taken that slot, and the thread carries on with a corrupt
        // handle. What follows depends on what the slot held: exceptions on the event thread
        // with no stack trace, Chromium's CHECK on a view rectangle it never received, or a
        // null it dereferences - SIGTRAP and SIGSEGV at shutdown on Linux and macOS, and only
        // sometimes, because it depends on the order the slots are handed out in.
        //
        // Returning false skips that branch entirely. CEF then takes the rectangle from
        // getViewRect and a scale factor of one, which is exactly what this used to fill in
        // by hand: the view is sized in device pixels by whoever draws it, so Chromium is never
        // told a scale. Nothing is lost, and the double delete is never reached.
        return false;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        // There is no screen. Answering with the view coordinates keeps popups and drag
        // feedback positioned relative to the page, which is the only frame of reference a
        // texture has.
        return new Point(viewPoint);
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        popupVisible = show;
        if (!show) {
            popupRect = new Rectangle(0, 0, 0, 0);
        }
        tellAboutPopup();
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        popupRect = new Rectangle(size);
        tellAboutPopup();
    }

    /** Called up from Chromium's thread, so nothing is allowed to escape. See onPaint. */
    private void tellAboutPopup() {
        try {
            sink.onPopup(popupVisible, popupRect());
        } catch (RuntimeException | Error consumerFailed) {
            report(consumerFailed);
        }
    }

    /** Where the popup surface belongs on the page, empty when none is showing. */
    public Rectangle popupRect() {
        return popupVisible ? new Rectangle(popupRect) : new Rectangle(0, 0, 0, 0);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
            ByteBuffer buffer, int width, int height) {
        // This is a call up from Chromium's own thread. Letting anything escape leaves an
        // exception pending across the boundary, where the best case is a lost frame and the
        // worst is undefined. Reported and dropped instead, because one bad frame is not a
        // reason to stop painting the rest.
        try {
            sink.onPaint(popup, dirtyRects, buffer, width, height);
            if (!paintListeners.isEmpty()) {
                CefPaintEvent event = new CefPaintEvent(browser, popup, dirtyRects, buffer, width, height);
                paintListeners.forEach(listener -> listener.accept(event));
            }
        } catch (RuntimeException | Error e) {
            report(e);
        }
    }

    private static void report(Throwable failure) {
        Thread thread = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(thread, failure);
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        // Reported for every move of the mouse, not only when it changes, so the report is
        // made here rather than passed on every time: a page is asked what it wants sixty
        // times a second and says the same thing.
        if (cursor != cursorType) {
            cursor = cursorType;
            try {
                events.onCursor(cursorType);
            } catch (RuntimeException | Error consumerFailed) {
                report(consumerFailed);
            }
        }
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        // Dragging out of the page and onto the desktop has nowhere to go from inside a game
        // window. Refusing it leaves drags inside the page working, which is what pages use.
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        // Nothing to update: see startDragging.
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.add(listener);
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.clear();
        if (listener != null) {
            paintListeners.add(listener);
        }
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.remove(listener);
    }
}
