package land.webgui;

import de.keksuccino.rinku.Rinku;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefFileDialogCallback;
import org.cef.handler.CefDialogHandler;

import java.util.List;
import java.util.Vector;

/**
 * Makes {@code <input type="file">} do something.
 *
 * A windowless browser cannot put up its own file chooser — CEF hands the decision to the
 * host, and with no handler the click does nothing whatsoever. So a ticket with a
 * screenshot attached, a schematic upload, a skin picker: all of them were dead controls
 * that gave the player no hint anything was wrong.
 *
 * The chooser is LWJGL's native one, not Swing's: Rinku boots AWT headless so the game
 * can own the window, and {@code JFileChooser} throws outright in that state. LWJGL is
 * already on the classpath because Minecraft itself runs on it.
 */
public final class WebviewFileDialogHandler {

    private WebviewFileDialogHandler() {}

    public static void register() {
        Rinku.getClient().getHandle().addDialogHandler(new CefDialogHandler() {
            @Override
            public boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title,
                                        String defaultFilePath, Vector<String> acceptFilters,
                                        CefFileDialogCallback callback) {
                return open(mode, title, defaultFilePath, acceptFilters, callback);
            }

            /**
             * The wider signature some CEF builds call instead. Both are default methods
             * on the interface, so implementing only one leaves the other silently doing
             * nothing on the builds that use it.
             */
            @Override
            public boolean onFileDialog(CefBrowser browser, FileDialogMode mode, String title,
                                        String defaultFilePath, Vector<String> acceptFilters,
                                        Vector<String> acceptExtensions, Vector<String> acceptDescriptions,
                                        CefFileDialogCallback callback) {
                Vector<String> filters = acceptExtensions != null && !acceptExtensions.isEmpty()
                        ? acceptExtensions
                        : acceptFilters;
                return open(mode, title, defaultFilePath, filters, callback);
            }
        });
    }

    /**
     * @return true always: this handler owns the dialog. Returning false asks CEF to show
     *         its own, which in a windowless browser means none at all.
     */
    private static boolean open(CefDialogHandler.FileDialogMode mode, String title,
                                String defaultPath, Vector<String> acceptFilters,
                                CefFileDialogCallback callback) {
        if (callback == null) {
            return true;
        }
        final String heading = title == null || title.isBlank() ? "Select a file" : title;
        final String start = defaultPath == null ? "" : defaultPath;
        final List<String> filters = acceptFilters == null ? List.of() : List.copyOf(acceptFilters);

        // On its own thread: CEF calls this on a thread it needs back, and a native modal
        // dialog does not return until the player has finished with it.
        Thread picker = new Thread(() -> {
            try {
                answer(mode, heading, start, filters, callback);
            } catch (Throwable t) {
                WebGUIMod.LOGGER.warn("webgui: the file chooser failed: {}", t.toString());
                callback.Cancel();
            }
        }, "webgui-file-dialog");
        picker.setDaemon(true);
        picker.start();
        return true;
    }

    private static void answer(CefDialogHandler.FileDialogMode mode, String title, String start,
                               List<String> acceptFilters, CefFileDialogCallback callback) {
        String chosen;
        boolean multiple = mode == CefDialogHandler.FileDialogMode.FILE_DIALOG_OPEN_MULTIPLE;

        if (mode == CefDialogHandler.FileDialogMode.FILE_DIALOG_OPEN_FOLDER) {
            chosen = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog(title, start);
        } else {
            // Stack-allocated: these are native strings, and the alternative is a manual
            // free on every path out of a call that can also throw. Each thread has its
            // own stack, and this one is ours.
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                List<String> globs = WebviewDownloadNames.globs(acceptFilters);
                org.lwjgl.PointerBuffer filters = null;
                if (!globs.isEmpty()) {
                    filters = stack.mallocPointer(globs.size());
                    for (String glob : globs) {
                        filters.put(stack.UTF8(glob));
                    }
                    filters.flip();
                }
                String description = globs.isEmpty() ? null : String.join(", ", globs);

                chosen = mode == CefDialogHandler.FileDialogMode.FILE_DIALOG_SAVE
                        ? org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(title, start, filters, description)
                        : org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(title, start, filters, description, multiple);
            }
        }

        if (chosen == null || chosen.isBlank()) {
            callback.Cancel();
            return;
        }
        Vector<String> selected = new Vector<>();
        // tinyfd returns multiple selections in one string, separated by pipes.
        for (String part : multiple ? chosen.split("\\|") : new String[] { chosen }) {
            String path = part.trim();
            if (!path.isEmpty()) {
                selected.add(path);
            }
        }
        if (selected.isEmpty()) {
            callback.Cancel();
            return;
        }
        WebGUIMod.LOGGER.info("webgui: the page was given {} file(s)", selected.size());
        callback.Continue(selected);
    }

}
