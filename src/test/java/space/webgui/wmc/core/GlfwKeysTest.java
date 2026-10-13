package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

/**
 * The key translation, which has no other way of being checked.
 *
 * <p>A wrong entry here is not a crash. It is a key that does nothing in a text field, or a
 * shortcut that fires the wrong thing, found by a player rather than by a build — and there is
 * no test that can be written in a game for "backspace deletes a character" that is cheaper
 * than this one.
 */
class GlfwKeysTest {

    @Test
    void lettersAndDigitsPassStraightThrough() {
        // GLFW numbers these by the character on the key and so does AWT, which is why most of
        // a keyboard needs no table at all.
        assertEquals(KeyEvent.VK_A, GlfwKeys.toAwtKey('A'));
        assertEquals(KeyEvent.VK_Z, GlfwKeys.toAwtKey('Z'));
        assertEquals(KeyEvent.VK_0, GlfwKeys.toAwtKey('0'));
        assertEquals(KeyEvent.VK_9, GlfwKeys.toAwtKey('9'));
    }

    @Test
    void theKeysATextFieldActuallyNeeds() {
        assertEquals(KeyEvent.VK_BACK_SPACE, GlfwKeys.toAwtKey(259));
        assertEquals(KeyEvent.VK_DELETE, GlfwKeys.toAwtKey(261));
        assertEquals(KeyEvent.VK_ENTER, GlfwKeys.toAwtKey(257));
        assertEquals(KeyEvent.VK_TAB, GlfwKeys.toAwtKey(258));
        assertEquals(KeyEvent.VK_LEFT, GlfwKeys.toAwtKey(263));
        assertEquals(KeyEvent.VK_RIGHT, GlfwKeys.toAwtKey(262));
        assertEquals(KeyEvent.VK_HOME, GlfwKeys.toAwtKey(268));
        assertEquals(KeyEvent.VK_END, GlfwKeys.toAwtKey(269));
        assertEquals(KeyEvent.VK_ESCAPE, GlfwKeys.toAwtKey(256));
    }

    @Test
    void bothSidesOfTheKeyboardAreTheSameKey() {
        // Left and right shift are different GLFW codes and one AWT key. A table that only
        // covers the left one leaves half a keyboard not holding shift.
        assertEquals(KeyEvent.VK_SHIFT, GlfwKeys.toAwtKey(340));
        assertEquals(KeyEvent.VK_SHIFT, GlfwKeys.toAwtKey(344));
        assertEquals(KeyEvent.VK_CONTROL, GlfwKeys.toAwtKey(341));
        assertEquals(KeyEvent.VK_CONTROL, GlfwKeys.toAwtKey(345));
        assertEquals(KeyEvent.VK_ALT, GlfwKeys.toAwtKey(342));
        assertEquals(KeyEvent.VK_ALT, GlfwKeys.toAwtKey(346));
    }

    @Test
    void theNumberPadIsContiguous() {
        for (int digit = 0; digit <= 9; digit++) {
            assertEquals(KeyEvent.VK_NUMPAD0 + digit, GlfwKeys.toAwtKey(320 + digit),
                    "numpad " + digit);
        }
        assertEquals(KeyEvent.VK_ENTER, GlfwKeys.toAwtKey(335), "the numpad enter is still enter");
    }

    @Test
    void functionKeysCoverF1ToF12() {
        for (int index = 0; index < 12; index++) {
            assertNotEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKey(290 + index), "F" + (index + 1));
        }
        assertEquals(KeyEvent.VK_F1, GlfwKeys.toAwtKey(290));
        assertEquals(KeyEvent.VK_F12, GlfwKeys.toAwtKey(301));
    }

    @Test
    void anythingUnknownIsUndefinedRatherThanWrong() {
        // Better than a plausible-looking guess: an undefined key still delivers its character
        // through typing, which is what a text field reads. A wrong key code does not.
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKey(9999));
        assertEquals(KeyEvent.VK_UNDEFINED, GlfwKeys.toAwtKey(-1));
    }

    @Test
    void modifiersBecomeTheMasksJavaCefReads() {
        assertEquals(0, GlfwKeys.toAwtModifiers(0));
        assertEquals(InputEvent.SHIFT_DOWN_MASK, GlfwKeys.toAwtModifiers(0x1));
        assertEquals(InputEvent.CTRL_DOWN_MASK, GlfwKeys.toAwtModifiers(0x2));
        assertEquals(InputEvent.ALT_DOWN_MASK, GlfwKeys.toAwtModifiers(0x4));
        assertEquals(InputEvent.META_DOWN_MASK, GlfwKeys.toAwtModifiers(0x8));
        assertEquals(InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK,
                GlfwKeys.toAwtModifiers(0x3));
    }
}
