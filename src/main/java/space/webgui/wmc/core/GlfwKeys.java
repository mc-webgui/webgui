package space.webgui.wmc.core;

/**
 * Translates the key codes a game produces into the ones Chromium expects.
 *
 * <p>Two namespaces meet here. The game speaks GLFW, because that is what its window gives it.
 * java-cef speaks AWT, because upstream expects a browser to be an AWT component. Neither is a
 * choice either side gets to make, so the translation lives in one place instead of in every
 * mod that wants a text field to work.
 *
 * <p>The numbers are written out rather than taken from LWJGL. They are part of the GLFW ABI
 * and cannot change, and a library that has no other reason to depend on a windowing toolkit
 * should not acquire one for a lookup table.
 *
 * <p>Codes with no AWT equivalent map to {@code VK_UNDEFINED}. A page still receives the
 * character through typing, which is what a text field actually reads.
 */
final class GlfwKeys {

    // GLFW modifier bits.
    private static final int MOD_SHIFT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_ALT = 0x0004;
    private static final int MOD_SUPER = 0x0008;

    private GlfwKeys() {
    }

    /** A GLFW key code as an AWT one, or {@code VK_UNDEFINED} when there is no equivalent. */
    static int toAwtKey(int glfw) {
        // Printable ASCII, which GLFW numbers by the character on the key. AWT agrees for
        // letters and digits, which is most of a keyboard.
        if (glfw >= 'A' && glfw <= 'Z') {
            return glfw;
        }
        if (glfw >= '0' && glfw <= '9') {
            return glfw;
        }
        return switch (glfw) {
            case 32 -> java.awt.event.KeyEvent.VK_SPACE;
            case 39 -> java.awt.event.KeyEvent.VK_QUOTE;
            case 44 -> java.awt.event.KeyEvent.VK_COMMA;
            case 45 -> java.awt.event.KeyEvent.VK_MINUS;
            case 46 -> java.awt.event.KeyEvent.VK_PERIOD;
            case 47 -> java.awt.event.KeyEvent.VK_SLASH;
            case 59 -> java.awt.event.KeyEvent.VK_SEMICOLON;
            case 61 -> java.awt.event.KeyEvent.VK_EQUALS;
            case 91 -> java.awt.event.KeyEvent.VK_OPEN_BRACKET;
            case 92 -> java.awt.event.KeyEvent.VK_BACK_SLASH;
            case 93 -> java.awt.event.KeyEvent.VK_CLOSE_BRACKET;
            case 96 -> java.awt.event.KeyEvent.VK_BACK_QUOTE;

            case 256 -> java.awt.event.KeyEvent.VK_ESCAPE;
            case 257 -> java.awt.event.KeyEvent.VK_ENTER;
            case 258 -> java.awt.event.KeyEvent.VK_TAB;
            case 259 -> java.awt.event.KeyEvent.VK_BACK_SPACE;
            case 260 -> java.awt.event.KeyEvent.VK_INSERT;
            case 261 -> java.awt.event.KeyEvent.VK_DELETE;
            case 262 -> java.awt.event.KeyEvent.VK_RIGHT;
            case 263 -> java.awt.event.KeyEvent.VK_LEFT;
            case 264 -> java.awt.event.KeyEvent.VK_DOWN;
            case 265 -> java.awt.event.KeyEvent.VK_UP;
            case 266 -> java.awt.event.KeyEvent.VK_PAGE_UP;
            case 267 -> java.awt.event.KeyEvent.VK_PAGE_DOWN;
            case 268 -> java.awt.event.KeyEvent.VK_HOME;
            case 269 -> java.awt.event.KeyEvent.VK_END;
            case 280 -> java.awt.event.KeyEvent.VK_CAPS_LOCK;
            case 281 -> java.awt.event.KeyEvent.VK_SCROLL_LOCK;
            case 282 -> java.awt.event.KeyEvent.VK_NUM_LOCK;
            case 283 -> java.awt.event.KeyEvent.VK_PRINTSCREEN;
            case 284 -> java.awt.event.KeyEvent.VK_PAUSE;

            case 290 -> java.awt.event.KeyEvent.VK_F1;
            case 291 -> java.awt.event.KeyEvent.VK_F2;
            case 292 -> java.awt.event.KeyEvent.VK_F3;
            case 293 -> java.awt.event.KeyEvent.VK_F4;
            case 294 -> java.awt.event.KeyEvent.VK_F5;
            case 295 -> java.awt.event.KeyEvent.VK_F6;
            case 296 -> java.awt.event.KeyEvent.VK_F7;
            case 297 -> java.awt.event.KeyEvent.VK_F8;
            case 298 -> java.awt.event.KeyEvent.VK_F9;
            case 299 -> java.awt.event.KeyEvent.VK_F10;
            case 300 -> java.awt.event.KeyEvent.VK_F11;
            case 301 -> java.awt.event.KeyEvent.VK_F12;

            case 320, 321, 322, 323, 324, 325, 326, 327, 328, 329 ->
                    java.awt.event.KeyEvent.VK_NUMPAD0 + (glfw - 320);
            case 330 -> java.awt.event.KeyEvent.VK_DECIMAL;
            case 331 -> java.awt.event.KeyEvent.VK_DIVIDE;
            case 332 -> java.awt.event.KeyEvent.VK_MULTIPLY;
            case 333 -> java.awt.event.KeyEvent.VK_SUBTRACT;
            case 334 -> java.awt.event.KeyEvent.VK_ADD;
            case 335 -> java.awt.event.KeyEvent.VK_ENTER;
            case 336 -> java.awt.event.KeyEvent.VK_EQUALS;

            case 340, 344 -> java.awt.event.KeyEvent.VK_SHIFT;
            case 341, 345 -> java.awt.event.KeyEvent.VK_CONTROL;
            case 342, 346 -> java.awt.event.KeyEvent.VK_ALT;
            case 343, 347 -> java.awt.event.KeyEvent.VK_META;
            case 348 -> java.awt.event.KeyEvent.VK_CONTEXT_MENU;

            default -> java.awt.event.KeyEvent.VK_UNDEFINED;
        };
    }

    /** GLFW modifier bits as the AWT extended masks java-cef reads. */
    static int toAwtModifiers(int glfwModifiers) {
        int awt = 0;
        if ((glfwModifiers & MOD_SHIFT) != 0) {
            awt |= java.awt.event.InputEvent.SHIFT_DOWN_MASK;
        }
        if ((glfwModifiers & MOD_CONTROL) != 0) {
            awt |= java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
        if ((glfwModifiers & MOD_ALT) != 0) {
            awt |= java.awt.event.InputEvent.ALT_DOWN_MASK;
        }
        if ((glfwModifiers & MOD_SUPER) != 0) {
            awt |= java.awt.event.InputEvent.META_DOWN_MASK;
        }
        return awt;
    }
}
