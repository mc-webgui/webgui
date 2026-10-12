package space.webgui.wmc.api;

import java.nio.ByteBuffer;
import java.util.OptionalLong;

/**
 * The pixels of the last paint.
 *
 * <p>Deliberately not a texture: this module knows nothing about graphics APIs, so it hands
 * over the bytes and lets the caller decide. The Minecraft-side module turns this into a
 * texture; a test turns it into a PNG.
 */
public interface Frame {

    /** Width in pixels, which is the view width times the device scale. */
    int width();

    /** Height in pixels. */
    int height();

    /**
     * Increases on every paint.
     *
     * <p>The reason this exists: uploading a texture costs more than comparing a long, and a
     * page that is not animating paints rarely. Keep the last value you uploaded and skip
     * the upload when it has not moved.
     */
    long revision();

    /** Whether anything has been painted yet. */
    boolean isEmpty();

    /**
     * What has changed since the frame was at {@code revision}.
     *
     * <p>The companion to {@link #revision()}, and the reason to prefer it: a page with a
     * cursor blinking in a text box paints a rectangle a few pixels across, and uploading the
     * whole picture for it costs a thousand times what the change did. Pass the revision you
     * last uploaded and upload only what comes back.
     *
     * <p>{@link Damage#NONE} when nothing has changed since. The whole frame whenever that
     * cannot be answered exactly — a revision older than the frame still remembers, a view
     * that has been resized since, or an implementation that does not track it — so a caller
     * that always uploads what it is given is always correct.
     */
    default Damage damageSince(long revision) {
        return new Damage(0, 0, width(), height());
    }

    /**
     * Copies the frame as BGRA, top row first, into {@code destination}.
     *
     * @throws java.nio.BufferOverflowException if the buffer is smaller than
     *                                          {@code width() * height() * 4}
     */
    void copyTo(ByteBuffer destination);

    /**
     * Copies one part of the frame as BGRA, packed: {@code region.width() * 4} bytes a row and
     * nothing between the rows, whatever the frame's own width is.
     *
     * <p>Packed rather than in place because that is what a graphics API wants handed to it for
     * a partial upload, and because the point of asking for part of a frame is not to touch the
     * rest of it. Pair it with {@link #damageSince(long)}.
     *
     * <p>The default copies the frame and then takes the part out of it, which is correct and
     * wastes the copy; an implementation that can read its own pixels should say so by
     * overriding this.
     *
     * @throws java.nio.BufferOverflowException if the buffer is smaller than
     *                                          {@code region.width() * region.height() * 4}
     */
    default void copyTo(ByteBuffer destination, Damage region) {
        ByteBuffer whole = ByteBuffer.allocateDirect(width() * height() * 4)
                .order(java.nio.ByteOrder.nativeOrder());
        copyTo(whole);
        for (int row = region.y(); row < region.y() + region.height(); row++) {
            int start = (row * width() + region.x()) * 4;
            whole.limit(start + region.width() * 4).position(start);
            destination.put(whole);
        }
    }

    /**
     * A platform shared-texture handle when the runtime is compositing on the GPU, allowing
     * the caller to skip the copy entirely.
     *
     * <p>Empty whenever the accelerated path is unavailable, which is the common case today.
     * Callers must always keep the {@link #copyTo} path working.
     */
    OptionalLong sharedTextureHandle();
}
