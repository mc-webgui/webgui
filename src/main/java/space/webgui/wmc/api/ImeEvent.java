package space.webgui.wmc.api;

import java.util.Objects;

/**
 * One step of input-method composition.
 *
 * @param text     the composition so far, or the finished text when {@code committed}
 * @param caret    caret position within {@code text}
 * @param committed whether this finishes the composition
 */
public record ImeEvent(String text, int caret, boolean committed) {

    public ImeEvent {
        Objects.requireNonNull(text, "text");
        if (caret < 0 || caret > text.length()) {
            throw new IllegalArgumentException("caret " + caret + " outside text of length " + text.length());
        }
    }

    /** Composition in progress, shown underlined by the page. */
    public static ImeEvent composing(String text, int caret) {
        return new ImeEvent(text, caret, false);
    }

    /** Composition finished; the page inserts the text. */
    public static ImeEvent commit(String text) {
        return new ImeEvent(text, text.length(), true);
    }

    /** Composition abandoned. */
    public static ImeEvent cancel() {
        return new ImeEvent("", 0, true);
    }
}
