package space.webgui.wmc.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalLong;
import space.webgui.wmc.api.Damage;
import space.webgui.wmc.api.Frame;

/**
 * The pixels of the last paint, kept until the next one.
 *
 * <p>Chromium hands over a buffer that is only valid for the duration of the callback, so the
 * frame is copied out of it. One copy is unavoidable; a second one is not, which is why the
 * destination is a direct buffer reused across paints rather than a new array each time. At
 * sixty frames a second a fresh two-megabyte array per paint is a hundred and twenty megabytes
 * a second of garbage, and the pauses that come with it.
 *
 * <p>Painting and reading happen on different threads — Chromium paints, the render thread
 * reads — so both take the same lock. It is held only for the copy.
 *
 * <p>What changed is kept as well as what is there, for the last {@link #REMEMBERED} paints.
 * Whoever draws asks what has moved since the revision they last uploaded, which need not be
 * the previous one: a game drawing at 60 frames a second while a page paints at 144 misses
 * two paints out of three, and asking only about the last one would leave the parts that
 * changed in the other two on screen as they were. Older than that and the answer is the whole
 * frame, which is correct and merely costs what every frame used to cost.
 */
final class CefFrame implements Frame {

    /**
     * How many paints back a caller may ask about.
     *
     * <p>Sixteen is a quarter of a second at sixty paints a second, and a caller that has not
     * drawn in a quarter of a second is not one worth keeping a ledger for.
     */
    private static final int REMEMBERED = 16;

    private final Object lock = new Object();

    /** The rectangle each of the last {@link #REMEMBERED} paints changed, by revision. */
    private final Damage[] changed = new Damage[REMEMBERED];

    private ByteBuffer pixels;
    private int width;
    private int height;
    private long revision;

    /**
     * The surface a select box draws into, kept beside the page rather than in it.
     *
     * <p>Chromium paints a menu the page opens over itself into a second surface and says
     * where it goes. The obvious thing is to write it into the page and be done; the reason
     * not to is what happens when it closes. The pixels it covered are gone, and getting them
     * back means keeping a copy of what was underneath, knowing when to put it back, and
     * knowing when that copy has gone stale — a state machine whose failures are a rectangle
     * of the wrong pixels left on screen.
     *
     * <p>So the page is never written to. The menu is held here and laid over the page as it
     * is read, which is the one moment both are known to be current. Closing it is forgetting
     * it, and the pixels underneath were never touched.
     */
    private ByteBuffer popupPixels;
    private int popupWidth;
    private int popupHeight;
    private int popupX;
    private int popupY;
    private boolean popupVisible;

    @Override
    public int width() {
        synchronized (lock) {
            return width;
        }
    }

    @Override
    public int height() {
        synchronized (lock) {
            return height;
        }
    }

    @Override
    public long revision() {
        synchronized (lock) {
            return revision;
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (lock) {
            return revision == 0;
        }
    }

    @Override
    public Damage damageSince(long since) {
        synchronized (lock) {
            if (revision == 0) {
                return Damage.NONE;
            }
            if (since >= revision) {
                return Damage.NONE;
            }
            if (since < revision - REMEMBERED || since < 0) {
                return everything();
            }
            Damage union = null;
            for (long at = since + 1; at <= revision; at++) {
                Damage one = changed[Math.floorMod(at, REMEMBERED)];
                if (one == null) {
                    return everything();
                }
                if (one.isEmpty()) {
                    // A paint that changed nothing of the picture. It still moved the
                    // revision, and it must not drag the rectangle out to the top left.
                    continue;
                }
                union = union == null ? one : cover(union, one);
            }
            return union == null ? Damage.NONE : union;
        }
    }

    private Damage everything() {
        return new Damage(0, 0, width, height);
    }

    /** The smallest rectangle holding both. */
    private static Damage cover(Damage first, Damage second) {
        int left = Math.min(first.x(), second.x());
        int top = Math.min(first.y(), second.y());
        int right = Math.max(first.x() + first.width(), second.x() + second.width());
        int bottom = Math.max(first.y() + first.height(), second.y() + second.height());
        return new Damage(left, top, right - left, bottom - top);
    }

    @Override
    public void copyTo(ByteBuffer destination) {
        synchronized (lock) {
            if (pixels == null) {
                return;
            }
            int start = destination.position();
            ByteBuffer source = pixels.duplicate();
            source.position(0).limit(width * height * 4);
            destination.put(source);
            layPopupOver(destination, start, new Damage(0, 0, width, height));
        }
    }

    @Override
    public void copyTo(ByteBuffer destination, Damage region) {
        synchronized (lock) {
            if (pixels == null) {
                return;
            }
            Damage part = clip(region);
            if (part.isEmpty()) {
                return;
            }
            int start = destination.position();
            ByteBuffer source = pixels.duplicate();
            int rowBytes = part.width() * 4;
            for (int row = part.y(); row < part.y() + part.height(); row++) {
                int from = (row * width + part.x()) * 4;
                source.limit(from + rowBytes).position(from);
                destination.put(source);
            }
            layPopupOver(destination, start, part);
        }
    }

    /**
     * Writes the menu over what was just copied out, where the two overlap.
     *
     * @param destination what the page was copied into, packed to {@code part}
     * @param start       where in it the copy began, since the caller's buffer may hold more
     * @param part        the region of the page that was copied
     */
    private void layPopupOver(ByteBuffer destination, int start, Damage part) {
        if (!popupVisible || popupPixels == null) {
            return;
        }
        // The size of what was painted, not the size in the rectangle: those can disagree for
        // a moment, and the pixels actually held are the ones that can be shown.
        Damage menu = clip(popupArea());
        int left = Math.max(menu.x(), part.x());
        int top = Math.max(menu.y(), part.y());
        int right = Math.min(menu.x() + menu.width(), part.x() + part.width());
        int bottom = Math.min(menu.y() + menu.height(), part.y() + part.height());
        if (right <= left || bottom <= top) {
            return;
        }
        int rowBytes = (right - left) * 4;
        for (int row = top; row < bottom; row++) {
            int from = ((row - popupY) * popupWidth + (left - popupX)) * 4;
            int to = start + ((row - part.y()) * part.width() + (left - part.x())) * 4;
            destination.put(to, popupPixels, from, rowBytes);
        }
    }

    /** Where the menu sits on the page, empty when there is not one. */
    private Damage popupArea() {
        if (!popupVisible || popupPixels == null || popupWidth <= 0 || popupHeight <= 0) {
            return Damage.NONE;
        }
        return new Damage(popupX, popupY, popupWidth, popupHeight);
    }

    @Override
    public OptionalLong sharedTextureHandle() {
        // The runtime composites in software, so there is no texture to hand over. Stated as
        // empty rather than left out: a caller that has an accelerated path can ask, and gets
        // a straight answer instead of having to guess from a configuration flag.
        return OptionalLong.empty();
    }

    /**
     * Takes a copy of what Chromium just painted. Called on a Chromium thread.
     *
     * @param dirty what Chromium says it changed, or {@code null} for "no idea", which is
     *              taken as all of it
     */
    void accept(ByteBuffer source, Damage dirty, int paintedWidth, int paintedHeight) {
        int needed = paintedWidth * paintedHeight * 4;
        synchronized (lock) {
            boolean resized = paintedWidth != width || paintedHeight != height;
            if (pixels == null || pixels.capacity() < needed) {
                pixels = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder());
            }
            ByteBuffer incoming = source.duplicate();
            incoming.rewind();
            incoming.limit(Math.min(incoming.capacity(), needed));
            pixels.clear();
            pixels.put(incoming);
            width = paintedWidth;
            height = paintedHeight;
            revision++;
            // A frame that changed size shares no pixel with the one before it, whatever
            // Chromium says about which part of it moved.
            changed[Math.floorMod(revision, REMEMBERED)] =
                    resized || dirty == null ? everything() : clip(dirty);
        }
    }

    /**
     * The menu's own pixels, which are not the page's. Called on a Chromium thread.
     *
     * <p>Counts as a paint of the page, because that is what it is to anyone reading the frame:
     * the same rectangle of it now shows something else.
     */
    void acceptPopup(ByteBuffer source, int paintedWidth, int paintedHeight) {
        int needed = paintedWidth * paintedHeight * 4;
        synchronized (lock) {
            if (popupPixels == null || popupPixels.capacity() < needed) {
                popupPixels = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder());
            }
            ByteBuffer incoming = source.duplicate();
            incoming.rewind();
            incoming.limit(Math.min(incoming.capacity(), needed));
            popupPixels.clear();
            popupPixels.put(incoming);
            popupWidth = paintedWidth;
            popupHeight = paintedHeight;
            marked(popupArea());
        }
    }

    /**
     * Where Chromium has put the menu, or that it has taken it away.
     *
     * <p>Both the place it has left and the place it has gone to have changed, so the damage
     * covers the two together. Taking it away damages only where it was, which is the whole of
     * the work that closing one needs: the page under it was never written to.
     */
    void placePopup(boolean visible, int x, int y, int menuWidth, int menuHeight) {
        synchronized (lock) {
            Damage before = popupArea();
            popupVisible = visible;
            popupX = x;
            popupY = y;
            if (visible && menuWidth > 0 && menuHeight > 0 && popupPixels == null) {
                // Nothing painted yet; the size is all that is known, and it stops the first
                // paint from being reported as a change to a rectangle of nothing.
                popupWidth = menuWidth;
                popupHeight = menuHeight;
            }
            Damage after = popupArea();
            marked(before.isEmpty() ? after : after.isEmpty() ? before : cover(before, after));
        }
    }

    /** Records a change to the picture that did not come from a paint of the page. */
    private void marked(Damage area) {
        revision++;
        changed[Math.floorMod(revision, REMEMBERED)] = clip(area);
    }

    /** Chromium has been seen to report a rectangle reaching past the frame it painted. */
    private Damage clip(Damage dirty) {
        int left = Math.max(0, dirty.x());
        int top = Math.max(0, dirty.y());
        int right = Math.min(width, dirty.x() + dirty.width());
        int bottom = Math.min(height, dirty.y() + dirty.height());
        if (right <= left || bottom <= top) {
            return Damage.NONE;
        }
        return new Damage(left, top, right - left, bottom - top);
    }
}
