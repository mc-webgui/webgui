package space.webgui.wmc.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.cef.browser.OffscreenBrowser;
import space.webgui.wmc.api.Channel;
import space.webgui.wmc.api.ConsoleLevel;
import space.webgui.wmc.api.Damage;
import space.webgui.wmc.api.Devtools;
import space.webgui.wmc.api.Frame;
import space.webgui.wmc.api.Input;
import space.webgui.wmc.api.WebView;
import space.webgui.wmc.api.WebViewListener;

/**
 * A view, as the API describes it, over a browser that knows nothing about the API.
 *
 * <p>This class is the whole reason no Chromium type appears in the contract. Everything above
 * it speaks in {@link WebView}, {@link Frame} and {@link Input}; everything below speaks CEF.
 * The engine underneath can be updated, or replaced, and this is the file that changes.
 */
final class CefWebView implements WebView {

    private final OffscreenBrowser browser;
    private final CefFrame frame;
    private final CefInput input;
    private final CefDevtools devtools;
    private final CopyOnWriteArrayList<WebViewListener> listeners = new CopyOnWriteArrayList<>();
    private final Runnable onClose;

    private volatile int width;
    private volatile int height;
    private volatile boolean closed;

    CefWebView(OffscreenBrowser browser, CefFrame frame, int width, int height,
            int debuggingPort, Runnable onClose) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.input = new CefInput(browser);
        this.devtools = new CefDevtools(browser, debuggingPort);
        this.width = width;
        this.height = height;
        this.onClose = Objects.requireNonNull(onClose, "onClose");
    }

    @Override
    public void load(String url) {
        alive();
        browser.loadURL(Objects.requireNonNull(url, "url"));
    }

    @Override
    public void loadHtml(String html, String baseUrl) {
        alive();
        Objects.requireNonNull(html, "html");
        // As a data URL rather than through java-cef's own string loading, which drops the
        // base url: a page loaded with no origin cannot fetch, cannot use modules and cannot
        // store anything, which makes "it works when I serve it" a confusing bug report.
        String encoded = Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
        browser.loadURL("data:text/html;base64," + encoded);
        if (baseUrl != null && !baseUrl.isBlank()) {
            listeners.forEach(listener -> listener.onLoadStart(baseUrl));
        }
    }

    @Override
    public String url() {
        alive();
        String current = browser.getURL();
        return current == null ? "" : current;
    }

    @Override
    public void reload(boolean ignoreCache) {
        alive();
        if (ignoreCache) {
            browser.reloadIgnoreCache();
        } else {
            browser.reload();
        }
    }

    @Override
    public void stop() {
        alive();
        browser.stopLoad();
    }

    @Override
    public void resize(int newWidth, int newHeight) {
        alive();
        if (newWidth <= 0 || newHeight <= 0) {
            throw new IllegalArgumentException("size must be positive, got " + newWidth + "x" + newHeight);
        }
        if (newWidth == width && newHeight == height) {
            return;
        }
        width = newWidth;
        height = newHeight;
        browser.resize(newWidth, newHeight);
    }

    @Override
    public void setFrameRate(int framesPerSecond) {
        alive();
        if (framesPerSecond <= 0 || framesPerSecond > 240) {
            throw new IllegalArgumentException("frame rate must be 1..240, got " + framesPerSecond);
        }
        browser.setWindowlessFrameRate(framesPerSecond);
    }

    @Override
    public Frame frame() {
        return frame;
    }

    @Override
    public void evaluate(String script, String sourceUrl, int startLine) {
        alive();
        Objects.requireNonNull(script, "script");
        browser.executeJavaScript(script, sourceUrl == null ? "" : sourceUrl, startLine);
    }

    @Override
    public void setZoom(double level) {
        alive();
        browser.setZoomLevel(level);
    }

    @Override
    public Input input() {
        alive();
        return input;
    }

    @Override
    public Channel channel(String name) {
        Objects.requireNonNull(name, "name");
        throw new UnsupportedOperationException(
                "channels are not implemented yet; see the roadmap in the README");
    }

    @Override
    public Devtools devtools() {
        alive();
        return devtools;
    }

    @Override
    public void addListener(WebViewListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeListener(WebViewListener listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isPopupOpen() {
        return !closed && !browser.popupRect().isEmpty();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        onClose.run();
        browser.close(true);
    }

    /** What Chromium painted, on a Chromium thread. */
    void onPaint(java.nio.ByteBuffer pixels, Damage dirty,
            int paintedWidth, int paintedHeight) {
        frame.accept(pixels, dirty, paintedWidth, paintedHeight);
    }

    void notifyLoadStart(String url) {
        listeners.forEach(listener -> listener.onLoadStart(url));
    }

    void notifyLoadEnd(String url, int httpStatus) {
        listeners.forEach(listener -> listener.onLoadEnd(url, httpStatus));
    }

    void notifyLoadFailed(String url, int errorCode, String description) {
        listeners.forEach(listener -> listener.onLoadFailed(url, errorCode, description));
    }

    void notifyTitle(String title) {
        listeners.forEach(listener -> listener.onTitle(title));
    }

    void notifyConsole(ConsoleLevel level, String message, String source, int line) {
        listeners.forEach(listener -> listener.onConsole(level, message, source, line));
    }

    void notifyCursor(String cssCursor) {
        listeners.forEach(listener -> listener.onCursor(cssCursor));
    }

    void notifyDownload(space.webgui.wmc.api.Download download) {
        listeners.forEach(listener -> listener.onDownload(download));
    }

    OffscreenBrowser browser() {
        return browser;
    }

    private void alive() {
        if (closed) {
            throw new IllegalStateException("this view has been closed");
        }
    }
}
