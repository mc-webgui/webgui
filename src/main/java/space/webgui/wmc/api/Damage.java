package space.webgui.wmc.api;

/**
 * The part of a frame that has changed, in pixels from the top left.
 *
 * <p>One rectangle, not a list. Chromium reports several when several parts of a page changed
 * at once, and the frame covers them with the smallest rectangle that holds them all: a caller
 * uploading this to a graphics API pays for one upload rather than several, and the pixels
 * that were covered but did not change are the same bytes either way. Where that bargain stops
 * paying — a page changing one pixel in each corner — it costs a full upload, which is what
 * the caller would have done anyway.
 */
public record Damage(int x, int y, int width, int height) {

    /** Nothing changed. */
    public static final Damage NONE = new Damage(0, 0, 0, 0);

    /** Whether there is anything to copy or upload at all. */
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    /** Whether this covers a frame of the given size entirely, and can be treated as "all of it". */
    public boolean covers(int frameWidth, int frameHeight) {
        return x <= 0 && y <= 0 && width >= frameWidth && height >= frameHeight;
    }
}
