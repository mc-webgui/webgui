package space.webgui.wmc.api;

/**
 * Sends input to a view.
 *
 * <p>Coordinates are CSS pixels within the view, with the origin at its top left. They are
 * not window pixels and not scaled GUI units: converting is the caller job, and the
 * Minecraft-side module does it. Mixing the two is the single most common way to end up
 * with a page that ignores every click, because the coordinates land outside it and nothing
 * reports an error.
 */
public interface Input {

    /**
     * Moves the pointer.
     *
     * @param leftDown whether the left button is held, which is what makes a drag a drag
     */
    void moveTo(double x, double y, boolean leftDown);

    /** The pointer left the view; the page sees a mouse-out. */
    void moveOut();

    /**
     * @param clickCount 1 for a click, 2 for a double click, and so on, which is how the page
     *                   tells a double click from two clicks
     */
    void press(MouseButton button, double x, double y, int clickCount);

    void release(MouseButton button, double x, double y);

    /** Scroll deltas in CSS pixels, positive meaning down and right. */
    void scroll(double x, double y, double deltaX, double deltaY);

    /**
     * A key going down.
     *
     * <p><strong>GLFW key codes, scan codes and modifier bits</strong>, which is what the game
     * gives you: pass them straight through. The runtime translates them to whatever the
     * browser engine underneath wants, so that the translation table lives in one place rather
     * than in every mod.
     *
     * <p>The scan code is not decoration. On Windows the browser engine works out which key was
     * pressed from it and ignores the key code entirely, so a key sent without one arrives at
     * the page as no key at all — no arrows, no backspace, no shortcuts, and nothing for a page
     * watching {@code keydown}. Every key event a game hands out carries it.
     */
    void keyDown(int keyCode, int scanCode, int modifiers);

    /** A key coming up, in the same GLFW codes as {@link #keyDown}. */
    void keyUp(int keyCode, int scanCode, int modifiers);

    /**
     * Types a character.
     *
     * <p>Separate from {@link #keyDown} because a key press and the character it produces are
     * different events: a layout decides what {@code shift+2} means, and the page needs the
     * result, not the key.
     */
    void type(char character, int modifiers);

    /**
     * Composition from an input method, for scripts that are not typed one key at a time.
     *
     * <p>Supported because the alternative is that players using Chinese, Japanese or Korean
     * input cannot type into a page at all.
     */
    void ime(ImeEvent event);

    /** Focus decides whether the page shows a caret and receives keys at all. */
    void focus(boolean focused);
}
