package space.webgui.wmc.api;

import java.util.Objects;

/**
 * What a view should be when it opens.
 *
 * <p>Immutable and built by chaining; every setter returns a new instance, so a spec can be
 * kept as a template and varied per view.
 */
public final class WebViewSpec {

    private final String url;
    private final int width;
    private final int height;
    private final int frameRate;
    private final boolean transparent;

    private WebViewSpec(String url, int width, int height, int frameRate, boolean transparent) {
        this.url = url;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.transparent = transparent;
    }

    /** A view on {@code url}, 1280x720, 60 fps, transparent. */
    public static WebViewSpec of(String url) {
        return new WebViewSpec(Objects.requireNonNull(url, "url"), 1280, 720, 60, true);
    }

    public WebViewSpec size(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size must be positive, got " + width + "x" + height);
        }
        return new WebViewSpec(url, width, height, frameRate, transparent);
    }

    public WebViewSpec frameRate(int framesPerSecond) {
        if (framesPerSecond <= 0 || framesPerSecond > 240) {
            throw new IllegalArgumentException("frame rate must be 1..240, got " + framesPerSecond);
        }
        return new WebViewSpec(url, width, height, framesPerSecond, transparent);
    }

    /**
     * Whether the page composites onto whatever is behind it.
     *
     * <p>On by default, because a view drawn over the game that paints an opaque background
     * hides the game. A page that wants a background should paint one.
     */
    public WebViewSpec transparent(boolean transparent) {
        return new WebViewSpec(url, width, height, frameRate, transparent);
    }

    public String url() {
        return url;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int frameRate() {
        return frameRate;
    }

    public boolean isTransparent() {
        return transparent;
    }

    @Override
    public String toString() {
        return "WebViewSpec[" + url + " " + width + "x" + height + " @" + frameRate + "fps"
                + (transparent ? " transparent" : "") + "]";
    }
}
