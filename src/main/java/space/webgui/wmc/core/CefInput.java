package space.webgui.wmc.core;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import org.cef.browser.OffscreenBrowser;
import space.webgui.wmc.api.ImeEvent;
import space.webgui.wmc.api.Input;
import space.webgui.wmc.api.MouseButton;

/**
 * Turns input into what Chromium expects.
 *
 * <p>java-cef speaks AWT events, because upstream expects a browser to be an AWT component
 * receiving them from the toolkit. There is no toolkit here, so the events are built by hand.
 * That is the whole job of this class, and it is the reason none of it appears in the API: a
 * mod should describe a click, not construct a {@code java.awt.event.MouseEvent}.
 *
 * <p>Coordinates arriving here are CSS pixels with the origin at the top left of the view,
 * which is what the API promises and what Chromium wants. No scaling happens here; the scale
 * is a property of the view.
 */
final class CefInput implements Input {

    private final OffscreenBrowser browser;
    private final Component source;

    private int modifiers;
    private double lastX;
    private double lastY;

    CefInput(OffscreenBrowser browser) {
        this.browser = browser;
        this.source = browser.eventSource();
    }

    @Override
    public void moveTo(double x, double y, boolean leftDown) {
        lastX = x;
        lastY = y;
        int mask = leftDown ? modifiers | MouseEvent.BUTTON1_DOWN_MASK : modifiers;
        browser.send(new MouseEvent(source, MouseEvent.MOUSE_MOVED, now(), mask,
                round(x), round(y), 0, false));
    }

    @Override
    public void moveOut() {
        // Chromium keeps a hover state, so a pointer that leaves without saying so leaves a
        // button looking hovered until something else happens to the page.
        browser.send(new MouseEvent(source, MouseEvent.MOUSE_EXITED, now(), modifiers,
                round(lastX), round(lastY), 0, false));
    }

    @Override
    public void press(MouseButton button, double x, double y, int clickCount) {
        lastX = x;
        lastY = y;
        modifiers |= downMask(button);
        browser.send(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, now(), modifiers,
                round(x), round(y), Math.max(1, clickCount), false, awtButton(button)));
    }

    @Override
    public void release(MouseButton button, double x, double y) {
        lastX = x;
        lastY = y;
        int mask = modifiers;
        modifiers &= ~downMask(button);
        browser.send(new MouseEvent(source, MouseEvent.MOUSE_RELEASED, now(), mask,
                round(x), round(y), 1, false, awtButton(button)));
    }

    @Override
    public void scroll(double x, double y, double deltaX, double deltaY) {
        // AWT carries one axis per event, so a diagonal scroll is two of them. Horizontal
        // first, because a page that only handles one tends to handle the vertical one.
        if (deltaX != 0) {
            browser.send(wheel(x, y, MouseWheelEvent.WHEEL_UNIT_SCROLL, deltaX, true));
        }
        if (deltaY != 0 || deltaX == 0) {
            browser.send(wheel(x, y, MouseWheelEvent.WHEEL_UNIT_SCROLL, deltaY, false));
        }
    }

    private MouseWheelEvent wheel(double x, double y, int scrollType, double amount, boolean horizontal) {
        int mask = horizontal ? modifiers | MouseWheelEvent.SHIFT_DOWN_MASK : modifiers;
        // How far the wheel turned, not merely which way. Keeping only the sign made every
        // event one smallest possible notch, so a page scrolled at a crawl however hard the
        // wheel was spun — and a trackpad's fine-grained scrolling arrived as the same step.
        int rotation = (int) Math.round(-amount);
        if (rotation == 0) {
            rotation = amount > 0 ? -1 : 1;
        }
        return new MouseWheelEvent(source, MouseWheelEvent.MOUSE_WHEEL, now(), mask,
                round(x), round(y), 0, false, scrollType, 3, rotation);
    }

    @Override
    public void keyDown(int keyCode, int scanCode, int keyModifiers) {
        modifiers = GlfwKeys.toAwtModifiers(keyModifiers);
        send(KeyEvent.KEY_PRESSED, keyCode, scanCode);
    }

    @Override
    public void keyUp(int keyCode, int scanCode, int keyModifiers) {
        modifiers = GlfwKeys.toAwtModifiers(keyModifiers);
        send(KeyEvent.KEY_RELEASED, keyCode, scanCode);
    }

    /**
     * Builds the event and puts the scan code in it, which is the part that matters.
     *
     * <p>Windows works out which key this was from the scan code alone — see {@link ScanCodes},
     * where the reason and the cost of reaching it are set out. Elsewhere the key code is what
     * is read, and carrying the scan code as well costs nothing.
     */
    private void send(int id, int keyCode, int scanCode) {
        KeyEvent event = new KeyEvent(source, id, now(), modifiers,
                GlfwKeys.toAwtKey(keyCode), KeyEvent.CHAR_UNDEFINED);
        ScanCodes.apply(event, scanCode);
        browser.send(event);
    }

    @Override
    public void type(char character, int keyModifiers) {
        // Separate from keyDown on purpose. A key press says which key; typing says which
        // character it produced, and the two differ for every keyboard layout that is not
        // American and for anything composed.
        browser.send(new KeyEvent(source, KeyEvent.KEY_TYPED, now(), GlfwKeys.toAwtModifiers(keyModifiers),
                KeyEvent.VK_UNDEFINED, character));
    }

    @Override
    public void ime(ImeEvent event) {
        throw new UnsupportedOperationException(
                "composed text input is not implemented yet; ordinary typing works");
    }

    @Override
    public void focus(boolean focused) {
        browser.setFocus(focused);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static int awtButton(MouseButton button) {
        return switch (button) {
            case LEFT -> MouseEvent.BUTTON1;
            case MIDDLE -> MouseEvent.BUTTON2;
            case RIGHT -> MouseEvent.BUTTON3;
        };
    }

    private static int downMask(MouseButton button) {
        return switch (button) {
            case LEFT -> MouseEvent.BUTTON1_DOWN_MASK;
            case MIDDLE -> MouseEvent.BUTTON2_DOWN_MASK;
            case RIGHT -> MouseEvent.BUTTON3_DOWN_MASK;
        };
    }
}
