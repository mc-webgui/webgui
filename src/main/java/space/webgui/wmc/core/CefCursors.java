package space.webgui.wmc.core;

import java.awt.Cursor;

/**
 * What Chromium asks the cursor to be, said in the language a page uses.
 *
 * <p>The API says cursors in CSS names — {@code pointer}, {@code text}, {@code ew-resize} —
 * because that is what the page itself wrote, and because a number out of someone else's header
 * has no business in a contract that names no Chromium types.
 *
 * <p><strong>The number is an AWT cursor, not a Chromium one.</strong> java-cef translates
 * Chromium's cursor into one of AWT's fourteen on the way up; upstream's own off-screen browser
 * gives the game away by passing it straight to {@code new Cursor(int)}, which accepts nothing
 * else. Read as Chromium's list instead — which is what the other embedder of this library
 * does, and what this did first — the names come out shifted: a link reports {@code sw-resize}
 * and text reports {@code pointer}, so the cursor changes over everything and is wrong over
 * everything. It was caught by logging the names and noticing that {@code text} never appeared
 * on a page made mostly of text.
 *
 * <p><strong>What that costs.</strong> AWT has no name for {@code not-allowed}, {@code grab},
 * {@code zoom-in}, {@code progress} or {@code cell}, so Chromium's are flattened to the default
 * arrow before this project ever sees them. A page asking for those gets an arrow, and there is
 * nothing to be done about it here: the distinction is lost below us.
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
