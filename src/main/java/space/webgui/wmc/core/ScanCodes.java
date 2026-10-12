package space.webgui.wmc.core;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

/**
 * Puts the scan code into a key event, because on Windows nothing else will do.
 *
 * <p>java-cef builds Chromium's key event out of an AWT one, and on Windows it does not read
 * {@code getKeyCode()}. It reads a private field named {@code scancode} and maps that to a
 * virtual key with {@code MapVirtualKey}. A key event made in Java has that field at zero, the
 * mapping gives zero back, and every key a page is sent arrives as no key at all: pages saw
 * {@code code=Escape key=Unidentified which=0} for W, A, S and D alike. Typing worked
 * throughout, because a typed character goes by another route and carries the character itself
 * - which is why this went unnoticed.
 *
 * <p><strong>Reaching the field.</strong> It is private in {@code java.awt.event}, a package the
 * desktop module exports and does not open, so ordinary reflection is refused - measured, in a
 * game: {@code module java.desktop does not "opens java.awt.event"}. What is left is
 * {@code sun.misc.Unsafe}, whose module opens itself to everyone and whose field accessors ask
 * no questions. That is what this uses, and it is not a happy answer: those accessors are
 * deprecated for removal, so this works until a JDK decides otherwise. When that day comes the
 * choice is a build of java-cef of our own, which is the thing this project exists not to have.
 *
 * <p>Refusing to work is not an option it takes. A key event with no scan code is what there
 * was before; if the field cannot be reached, keys go out as they went out then, and pages
 * receive the typed characters that always worked.
 */
final class ScanCodes {

    private static final Object UNSAFE = unsafe();
    private static final long OFFSET = offset();

    private ScanCodes() {
    }

    /** Whether a key event can be given a scan code in this process. */
    static boolean available() {
        return UNSAFE != null && OFFSET != -1;
    }

    /**
     * Writes the scan code the window system reported into the event.
     *
     * @param scanCode as GLFW reports it, which on Windows is the hardware scan code the native
     *                 side is about to map back to a virtual key
     */
    static void apply(KeyEvent event, int scanCode) {
        if (!available() || scanCode == 0) {
            return;
        }
        try {
            UNSAFE.getClass().getMethod("putLong", Object.class, long.class, long.class)
                    .invoke(UNSAFE, event, OFFSET, (long) scanCode);
        } catch (ReflectiveOperationException | RuntimeException | Error refused) {
            // Nothing to do about it, and nothing useful to say at every keystroke.
        }
    }

    private static Object unsafe() {
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field singleton = type.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            return singleton.get(null);
        } catch (ReflectiveOperationException | RuntimeException | Error absent) {
            return null;
        }
    }

    private static long offset() {
        if (UNSAFE == null) {
            return -1;
        }
        try {
            Field scancode = KeyEvent.class.getDeclaredField("scancode");
            return (long) UNSAFE.getClass().getMethod("objectFieldOffset", Field.class)
                    .invoke(UNSAFE, scancode);
        } catch (ReflectiveOperationException | RuntimeException | Error changed) {
            return -1;
        }
    }
}
