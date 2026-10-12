package space.webgui.wmc.core;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

/**
 * Puts the scan code into an AWT key event.
 *
 * <p>On Windows java-cef ignores {@code getKeyCode()} and reads a private {@code scancode}
 * field, mapping it with {@code MapVirtualKey}. Left at zero, every key reaches the page as no
 * key at all.
 *
 * <p>{@code java.awt.event} is exported but not opened, so the field is reached through
 * {@code sun.misc.Unsafe}, whose accessors are deprecated for removal. If it cannot be reached,
 * keys go out without a scan code, as they did before, and typed characters still work.
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
