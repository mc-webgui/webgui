package space.webgui.wmc.core;

import java.awt.Cursor;

/**
 * Chromium's cursor, as the CSS name a page would have written.
 *
 * <p>The number java-cef reports is an AWT cursor, not a Chromium one — it translates on the
 * way up. Reading it against Chromium's list gives names shifted by a few: a link reports
 * {@code sw-resize}, text reports {@code pointer}.
 *
 * <p>AWT has no name for {@code not-allowed}, {@code grab}, {@code zoom-in} or {@code cell},
 * so those are already flattened to the arrow before they reach here.
 */
final class CefCursors {

    private CefCursors() {
    }

    /**
     * @param cursorType the cursor as java-cef reports it, which is a {@link Cursor} type
     * @return the CSS name, or {@code default} for anything else
     */
    static String cssName(int cursorType) {
        return switch (cursorType) {
            case Cursor.DEFAULT_CURSOR -> "default";
            case Cursor.CROSSHAIR_CURSOR -> "crosshair";
            case Cursor.TEXT_CURSOR -> "text";
            case Cursor.WAIT_CURSOR -> "wait";
            case Cursor.SW_RESIZE_CURSOR -> "sw-resize";
            case Cursor.SE_RESIZE_CURSOR -> "se-resize";
            case Cursor.NW_RESIZE_CURSOR -> "nw-resize";
            case Cursor.NE_RESIZE_CURSOR -> "ne-resize";
            case Cursor.N_RESIZE_CURSOR -> "n-resize";
            case Cursor.S_RESIZE_CURSOR -> "s-resize";
            case Cursor.W_RESIZE_CURSOR -> "w-resize";
            case Cursor.E_RESIZE_CURSOR -> "e-resize";
            case Cursor.HAND_CURSOR -> "pointer";
            case Cursor.MOVE_CURSOR -> "move";
            default -> "default";
        };
    }
}
