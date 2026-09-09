package land.webgui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a page's suggested download name into one that is safe to write.
 *
 * The suggestion arrives from web content — a Content-Disposition header, in practice —
 * so it is attacker-controlled text on its way to a filesystem call. This is the only
 * thing between the two, which is why it lives apart from the CEF handler that uses it:
 * free of game and browser classes, it can be tested directly on every version.
 */
public final class WebviewDownloadNames {

    /**
     * Windows refuses these as file names whatever the extension, and a write to one
     * succeeds while going nowhere — so the download would look like it worked and leave
     * nothing behind.
     */
    private static final Set<String> RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private static final int MAX_LENGTH = 120;

    private WebviewDownloadNames() {}

    /**
     * A file name and nothing else.
     *
     * Separators and dot segments go, leaving something that cannot name a place other
     * than the folder its caller chose.
     */
    public static String safeName(String suggested) {
        String name = suggested == null ? "" : suggested.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        StringBuilder cleaned = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            // Reserved on Windows, awkward everywhere, or a control character.
            boolean bad = c < 0x20 || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|' || c == '/' || c == '\\';
            cleaned.append(bad ? '_' : c);
        }
        name = cleaned.toString().trim();

        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            name = "download";
        }

        String stem = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (RESERVED.contains(stem.toUpperCase(Locale.ROOT))) {
            name = "_" + name;
        }
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }

    /** The given name, or the same with a counter, so a second download never overwrites the first. */
    public static Path free(Path directory, String name) {
        Path candidate = directory.resolve(name);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            Path next = directory.resolve(stem + " (" + i + ")" + extension);
            if (!Files.exists(next)) {
                return next;
            }
        }
        return directory.resolve(stem + "-" + System.currentTimeMillis() + extension);
    }

}
