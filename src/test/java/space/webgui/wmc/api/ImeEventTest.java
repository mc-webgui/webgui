package space.webgui.wmc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImeEventTest {

    @Test
    void compositionIsNotACommit() {
        assertFalse(ImeEvent.composing("\u306B\u307B", 2).committed());
        assertTrue(ImeEvent.commit("\u65E5\u672C").committed());
    }

    @Test
    void commitPutsTheCaretAtTheEnd() {
        assertEquals(2, ImeEvent.commit("ab").caret());
    }

    @Test
    void aCaretOutsideTheTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ImeEvent("ab", 3, false));
        assertThrows(IllegalArgumentException.class, () -> new ImeEvent("ab", -1, false));
    }
}
