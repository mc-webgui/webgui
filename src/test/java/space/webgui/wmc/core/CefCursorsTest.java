package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Cursor;
import org.junit.jupiter.api.Test;

/**
 * Which cursor the page asked for.
 *
 * <p>Worth a test for a reason that is not obvious from the code: the number java-cef reports
 * is an AWT cursor, and it was read as Chromium's own list first. Both lists start with the
 * ordinary arrow at zero and disagree about nearly everything after it, so the mistake is
 * invisible in a screenshot — the cursor changes as the mouse moves, and is the wrong shape
 * everywhere. What gave it away was that {@code text} never once appeared.
 */
class CefCursorsTest {

    @Test
    void theHandIsWhatCssCallsPointer() {
        // The one that matters most: it is what says a thing can be clicked. Read as
        // Chromium's list this number is a resize arrow.
        assertEquals("pointer", CefCursors.cssName(Cursor.HAND_CURSOR));
    }

    @Test
    void theBeamOverTextIsText() {
        // And read as Chromium's list, this one is the hand - the other half of the swap.
        assertEquals("text", CefCursors.cssName(Cursor.TEXT_CURSOR));
    }

    @Test
    void theArrowIsTheDefault() {
        assertEquals("default", CefCursors.cssName(Cursor.DEFAULT_CURSOR));
    }

    @Test
    void theResizeArrowsKeepTheirDirections() {
        assertEquals("e-resize", CefCursors.cssName(Cursor.E_RESIZE_CURSOR));
        assertEquals("w-resize", CefCursors.cssName(Cursor.W_RESIZE_CURSOR));
        assertEquals("n-resize", CefCursors.cssName(Cursor.N_RESIZE_CURSOR));
        assertEquals("nw-resize", CefCursors.cssName(Cursor.NW_RESIZE_CURSOR));
        assertEquals("move", CefCursors.cssName(Cursor.MOVE_CURSOR));
    }

    @Test
    void anythingElseIsAnArrowRatherThanAGuess() {
        // AWT has fourteen shapes and CSS has thirty. A number outside the list is either a
        // cursor AWT has no name for or a library that has changed underneath; either way the
        // arrow is right and inventing a name is not.
        assertEquals("default", CefCursors.cssName(99));
        assertEquals("default", CefCursors.cssName(-1));
    }
}
