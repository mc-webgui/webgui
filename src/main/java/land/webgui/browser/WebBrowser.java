package land.webgui.browser;

import java.util.concurrent.CopyOnWriteArrayList;
import land.webgui.WebSession;
import org.cef.CefClient;
import space.webgui.wmc.api.ConsoleLevel;
import space.webgui.wmc.api.Download;
import space.webgui.wmc.api.WebViewListener;
import space.webgui.wmc.api.MouseButton;
import space.webgui.wmc.api.WebRuntime;
import space.webgui.wmc.api.WebView;
import space.webgui.wmc.api.WebViewSpec;
import space.webgui.wmc.api.RuntimeState;
import space.webgui.wmc.core.CefRuntime;
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;*/
//? }

/**
 * One browser, as the rest of this mod expects to find one.
 *
 * <p>This is where the engine stops being a library and starts being ours. Everything above
 * it is written the way it was written against Rinku — same method names, same arguments —
 * and everything below is {@link WebView}, which knows nothing about Minecraft.
 *
 * <p>One difference runs through the whole class and cannot be hidden: <strong>a browser is
 * not ready when it is asked for.</strong> Rinku handed one back made; here opening a page is
 * a future, because the thread that would have to wait is the thread that drives Chromium.
 * So this hands back a handle immediately and the page arrives into it. Until it does, what a
 * caller asked for is remembered rather than refused — a size, a zoom, an address — and input
 * is dropped, because there is nothing drawn yet to have aimed at.
 */
public final class WebBrowser {

    /** How close two clicks must be, in milliseconds, for the page to be told it was a double. */
    private static final long DOUBLE_CLICK_MILLIS = 500;

    private final BrowserTexture texture = new BrowserTexture();
    private final BrowserCursor cursor = new BrowserCursor();

    private volatile WebView view;
    private volatile boolean closed;

    // Remembered until there is a browser to tell.
    private String pendingUrl;
    private int pendingWidth;
    private int pendingHeight;
    private double pendingZoom;
    private boolean pendingFocus;
    private boolean hasPendingZoom;

    // Enough state to describe a gesture the API asks about and the game does not report.
    private boolean leftDown;
    private long lastClickAt;
    private int lastClickButton = -1;
    private int clickCount;

    private WebBrowser() {
    }

    // --- the runtime ---------------------------------------------------------------------

    /** Whether the browser engine is up. */
    public static boolean ready() {
        return WebRuntime.get().state() == RuntimeState.READY;
    }

    /** Whether the engine has got far enough to be worth waiting for. */
    public static boolean usable() {
        RuntimeState state = WebRuntime.get().state();
        return state != RuntimeState.UNAVAILABLE && state != RuntimeState.FAILED;
    }

    /** Runs something once the engine is up, or never if it never comes up. */
    public static void whenReady(Runnable action) {
        WebRuntime.get().whenReady().thenRun(action);
    }

    /**
     * The CEF client, for the things the contract does not describe yet.
     *
     * <p>The page-to-game bridge is a CEF message router, and until that is a channel this is
     * how it is reached. Null before the engine is up.
     */
    public static CefClient client() {
        WebRuntime runtime = WebRuntime.get();
        return runtime instanceof CefRuntime cef ? cef.client() : null;
    }

    /**
     * Ends Chromium so the process can exit.
     *
     * <p>java-cef pulls in AWT, whose event thread is not a daemon and outlives Minecraft's
     * main while Chromium is up. Called from the client-stopping hook of either loader.
     */
    public static void shutdown() {
        WebRuntime runtime = WebRuntime.get();
        if (runtime instanceof CefRuntime cef) {
            cef.shutdown();
        }
    }

    // --- listening ------------------------------------------------------------------------

    /**
     * What a page does, reported with the browser it happened to.
     *
     * <p>Registered once for every browser there will ever be, which is how this mod has
     * always done it. It cannot be done the other way round — by handing CEF a handler of our
     * own — because java-cef keeps one display, load and download handler per client and a
     * second one silently replaces the first. The engine's own plumbing is attached to those
     * slots, so a mod that takes one takes the frames with it.
     */
    public interface Listener {

        default void onLoadStart(WebBrowser browser, String url) {
        }

        default void onLoadEnd(WebBrowser browser, String url, int httpStatus) {
        }

        default void onLoadFailed(WebBrowser browser, String url, int errorCode, String description) {
        }

        default void onConsole(WebBrowser browser, ConsoleLevel level, String message,
                String source, int line) {
        }

        default void onDownload(WebBrowser browser, Download download) {
        }
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    /** Adds a listener that every browser opened from now on will report to. */
    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    // --- making one ----------------------------------------------------------------------

    /**
     * Opens a page.
     *
     * <p>Returns straight away with a browser that has not loaded anything yet. Drawing it
     * before then draws nothing, which is what {@link #isTextureReady} is for.
     */
    public static WebBrowser create(String url, boolean transparent) {
        WebBrowser browser = new WebBrowser();
        WebViewSpec spec = WebViewSpec.of(url).transparent(transparent);
        WebRuntime.get().open(spec).whenComplete((opened, failure) -> {
            if (failure != null || opened == null) {
                return;
            }
            if (browser.closed) {
                opened.close();
                return;
            }
            browser.adopt(opened);
        });
        return browser;
    }

    private synchronized void adopt(WebView opened) {
        this.view = opened;
        opened.addListener(new WebViewListener() {
            @Override
            public void onLoadStart(String url) {
                LISTENERS.forEach(listener -> listener.onLoadStart(WebBrowser.this, url));
            }

            @Override
            public void onLoadEnd(String url, int httpStatus) {
                LISTENERS.forEach(listener -> listener.onLoadEnd(WebBrowser.this, url, httpStatus));
            }

            @Override
            public void onLoadFailed(String url, int errorCode, String description) {
                LISTENERS.forEach(listener ->
                        listener.onLoadFailed(WebBrowser.this, url, errorCode, description));
            }

            @Override
            public void onConsole(ConsoleLevel level, String message, String source, int line) {
                LISTENERS.forEach(listener ->
                        listener.onConsole(WebBrowser.this, level, message, source, line));
            }

            @Override
            public void onDownload(Download download) {
                LISTENERS.forEach(listener -> listener.onDownload(WebBrowser.this, download));
            }

            @Override
            public void onCursor(String cssCursor) {
                // Rinku changed the window's cursor itself and nothing here ever asked it to,
                // so losing it was silent: a page where the pointer never becomes a hand reads
                // as a picture of a page. Only the browser the player is actually looking at
                // may touch it, and only on the thread that owns the window.
                if (WebSession.browser() != WebBrowser.this) {
                    return;
                }
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client != null) {
                    client.execute(() -> {
                        if (!closed && WebSession.browser() == WebBrowser.this) {
                            cursor.set(cssCursor);
                        }
                    });
                }
            }
        });
        if (pendingWidth > 0 && pendingHeight > 0) {
            opened.resize(pendingWidth, pendingHeight);
        }
        if (hasPendingZoom) {
            opened.setZoom(pendingZoom);
        }
        if (pendingUrl != null) {
            opened.load(pendingUrl);
            pendingUrl = null;
        }
        if (pendingFocus) {
            opened.input().focus(true);
        }
    }

    /** The view underneath, or null until the page has been opened. */
    public WebView view() {
        return view;
    }

    // --- drawing -------------------------------------------------------------------------

    /**
     * Puts whatever the page has painted since last time on the GPU.
     *
     * <p>Called from the render thread, by whoever is about to draw the page.
     */
    //? if >=1.21.5 {
    public Identifier getTextureIdentifier() {
    //? } else {
    /*public ResourceLocation getTextureIdentifier() {*/
    //? }
        WebView current = view;
        if (current == null || closed || current.isClosed()) {
            return null;
        }
        return texture.upload(current.frame());
    }

    /** Whether there is a picture to draw. */
    public boolean isTextureReady() {
        WebView current = view;
        return current != null && !closed && !current.isClosed() && !current.frame().isEmpty();
    }

    // --- what the page is showing --------------------------------------------------------

    public synchronized void loadURL(String url) {
        WebView current = view;
        if (current == null) {
            pendingUrl = url;
            return;
        }
        current.load(url);
    }

    public String getURL() {
        WebView current = view;
        if (current == null) {
            return pendingUrl;
        }
        return current.url();
    }

    public void reload() {
        WebView current = view;
        if (current != null) {
            current.reload(false);
        }
    }

    public synchronized void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        pendingWidth = width;
        pendingHeight = height;
        WebView current = view;
        if (current != null) {
            current.resize(width, height);
        }
    }

    public synchronized void setZoomLevel(double level) {
        pendingZoom = level;
        hasPendingZoom = true;
        WebView current = view;
        if (current != null) {
            current.setZoom(level);
        }
    }

    public void executeJavaScript(String script, String url, int line) {
        WebView current = view;
        if (current != null) {
            current.evaluate(script, url, line);
        }
    }

    public void setWindowlessFrameRate(int framesPerSecond) {
        WebView current = view;
        if (current != null) {
            current.setFrameRate(framesPerSecond);
        }
    }

    // --- input ---------------------------------------------------------------------------

    public synchronized void setFocus(boolean focused) {
        pendingFocus = focused;
        WebView current = view;
        if (current != null) {
            current.input().focus(focused);
        }
    }

    public void sendMouseMove(int x, int y) {
        WebView current = view;
        if (current != null) {
            current.input().moveTo(x, y, leftDown);
        }
    }

    public void sendMousePress(int x, int y, int button) {
        WebView current = view;
        if (current == null) {
            return;
        }
        if (button == 0) {
            leftDown = true;
        }
        current.input().press(button(button), x, y, countClick(button));
    }

    public void sendMouseRelease(int x, int y, int button) {
        WebView current = view;
        if (current == null) {
            return;
        }
        if (button == 0) {
            leftDown = false;
        }
        current.input().release(button(button), x, y);
    }

    public void sendMouseWheel(int x, int y, double vertical, int horizontal) {
        WebView current = view;
        if (current != null) {
            // How far a notch carries a page is a taste, and tastes differ by mouse: it is a
            // setting rather than a number chosen here.
            int notches = land.webgui.WebGUIClientConfig.scrollNotches();
            current.input().scroll(x, y, -horizontal * notches, -vertical * notches);
        }
    }

    public void sendKeyPress(int keyCode, int scanCode, int modifiers) {
        WebView current = view;
        if (current == null) {
            return;
        }
        //? if >26.2 {
        /*// The window is SDL's from 26.3, so what arrives is a USB scan code and SDL's
        // modifier bits. The engine speaks GLFW; translating here keeps it that way.
        int key = SdlKeys.toGlfwKey(keyCode);
        int scan = SdlKeys.toWindowsScanCode(keyCode);
        int mods = SdlKeys.toGlfwModifiers(modifiers);
        *///? } else {
        int key = keyCode;
        int scan = scanCode;
        int mods = modifiers;
        //? }
        current.input().keyDown(key, scan, mods);
        // Enter produces no character event in GLFW or SDL, so charTyped is never called for
        // it, and Chromium inserts a newline on the character rather than the key.
        if (key == KEY_ENTER || key == KEY_KEYPAD_ENTER) {
            current.input().type('\r', mods);
        }
    }

    // GLFW key codes, spelled out because GLFW is off the classpath from 26.3.
    private static final int KEY_ENTER = 257;
    private static final int KEY_KEYPAD_ENTER = 335;

    public void sendKeyRelease(int keyCode, int scanCode, int modifiers) {
        WebView current = view;
        if (current != null) {
            //? if >26.2 {
            /*// The window is SDL's from 26.3, so what arrives is a USB scan code and SDL's
            // modifier bits. The engine speaks GLFW; translating here keeps it that way.
            current.input().keyUp(SdlKeys.toGlfwKey(keyCode), SdlKeys.toWindowsScanCode(keyCode),
                    SdlKeys.toGlfwModifiers(modifiers));
            *///? } else {
            current.input().keyUp(keyCode, scanCode, modifiers);
            //? }
        }
    }

    public void sendKeyTyped(char character, int modifiers) {
        WebView current = view;
        if (current != null) {
            //? if >26.2 {
            /*current.input().type(character, SdlKeys.toGlfwModifiers(modifiers));
            *///? } else {
            current.input().type(character, modifiers);
            //? }
        }
    }

    // Asks for the page's cursor on this frame; see BrowserCursor for why it is per frame.
    //? if >26.2 {
    /*public void requestCursor(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        cursor.request(graphics);
    }
    *///? }

    /**
     * How many clicks in a row this is.
     *
     * <p>A page is told, because selecting a word is a double click and a line is a triple,
     * and a page that is always told "one" cannot do either. The game reports each press on
     * its own, so the counting is here.
     */
    private synchronized int countClick(int button) {
        long now = System.currentTimeMillis();
        if (button == lastClickButton && now - lastClickAt < DOUBLE_CLICK_MILLIS) {
            clickCount = Math.min(clickCount + 1, 3);
        } else {
            clickCount = 1;
        }
        lastClickAt = now;
        lastClickButton = button;
        return clickCount;
    }

    private static MouseButton button(int button) {
        //? if >26.2 {
        /*// SDL numbers them from one, and puts the middle button where GLFW put the right.
        // Silent if got wrong: a left click would simply open a context menu instead.
        return switch (button) {
            case 2 -> MouseButton.MIDDLE;
            case 3 -> MouseButton.RIGHT;
            default -> MouseButton.LEFT;
        };
        *///? } else {
        return switch (button) {
            case 1 -> MouseButton.RIGHT;
            case 2 -> MouseButton.MIDDLE;
            default -> MouseButton.LEFT;
        };
        //? }
    }

    // --- developer tools -----------------------------------------------------------------

    /**
     * Listens to what the page reports over the developer-tools protocol.
     *
     * <p>Console messages, uncaught exceptions and failed requests arrive here. Each domain
     * stays silent until {@link #callDevTools} enables it, so subscribing alone reports
     * nothing — which is deliberate: a page nobody is debugging should cost nothing.
     */
    public void onDevToolsEvent(java.util.function.BiConsumer<String, String> handler) {
        WebView current = view;
        if (current != null) {
            current.devtools().onEvent(handler);
        }
    }

    /** Asks the page something over the protocol, or tells it to start reporting a domain. */
    public void callDevTools(String method) {
        WebView current = view;
        if (current != null) {
            current.devtools().call(method, null);
        }
    }

    /**
     * Opens this page's inspector in the player's own browser.
     *
     * <p>Not in a window of the game's: java-cef hands the inspector to Chromium's own pop-up
     * and leaves the window information empty, and that pop-up belongs to a thread with no
     * message loop — it draws once and then answers nothing. What works is the debugging port,
     * and the address is asked for rather than assembled, because Chromium issues one per page
     * and guessing it opens the tools on whichever page happens to be first.
     *
     * @param whenFailed told why, if there is no address to open
     */
    public void openDevTools(java.util.function.Consumer<String> whenFailed) {
        WebView current = view;
        if (current == null) {
            whenFailed.accept("there is no page open");
            return;
        }
        current.devtools().frontendUrl().whenComplete((url, failure) -> {
            if (failure != null || url == null || url.isBlank()) {
                whenFailed.accept(failure == null ? "Chromium named no address for this page"
                        : failure.getMessage());
                return;
            }
            CefRuntime.settings().urlOpener().accept(url);
        });
    }

    // --- the end -------------------------------------------------------------------------

    public void close() {
        closed = true;
        WebView current = view;
        view = null;
        if (current != null) {
            current.close();
        }
        // Taken out of the game's texture register from here, not from the texture's own
        // close: the game walks that register when it shuts down and closing it from inside
        // the walk is what used to kill it.
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null) {
            mc.execute(texture::unregister);
        } else {
            texture.close();
        }
        // Always, or a screen closed while the page wanted a text beam leaves the player
        // holding one in the world, and nothing in the game would ever put it right.
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null) {
            client.execute(cursor::reset);
        }
    }
}
