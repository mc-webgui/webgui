package space.webgui.wmc.core;

import java.nio.file.Path;
import java.util.Locale;
import org.cef.SystemBootstrap;

/**
 * Loads the libraries out of an installed bundle.
 *
 * <p>Order matters and the reason is not obvious. The JNI library is a thin wrapper whose
 * every symbol comes from Chromium, so Chromium has to be in the process first; loaded by the
 * plain name it would be looked for on the library path, which is not where it is. Loading it
 * by absolute path first means the dependent lookup finds the copy already loaded.
 *
 * <p>Once per process, and the second call with the same bundle is not an error — two mods
 * asking for a browser is the ordinary case, and only one of them can be first.
 */
public final class CefLoader {

    private static Path loadedFrom;

    private CefLoader() {
    }

    /** Loads the bundle, or returns quietly if this one is already loaded. */
    public static synchronized void load(BundleLayout layout) {
        if (loadedFrom != null) {
            if (loadedFrom.equals(layout.root())) {
                return;
            }
            throw new InstallException("a browser runtime is already loaded from " + loadedFrom
                    + "; a second one from " + layout.root() + " cannot be loaded into the same process");
        }

        if (isWindows()) {
            // Chromium's own loader shim. Loading it first is what upstream does on Windows,
            // and skipping it produces a failure inside Chromium rather than here.
            layout.nativeLibrary("chrome_elf").ifPresent(path -> System.load(path.toString()));
        }
        if (layout.frameworkDirectory().isEmpty()) {
            System.load(layout.library().toString());
        }
        // On macOS the framework is not loaded here at all. It is a framework rather than a
        // plain library, and Chromium loads it itself from the path it is given on the command
        // line; loading the binary by hand first puts a second copy of it in the process.

        SystemBootstrap.setLoader(name -> {
            // The bundle first, the JVM's own library path second. java-cef asks for jawt as
            // well as its own libraries, and jawt belongs to the JDK - refusing to look
            // outside the bundle turns a library that is present into one that is missing.
            Path library = layout.nativeLibrary(name).orElse(null);
            if (library != null) {
                System.load(library.toString());
            } else {
                System.loadLibrary(name);
            }
        });

        loadedFrom = layout.root();
    }

    /** Where the loaded bundle came from, or null if none is loaded yet. */
    public static synchronized Path loadedFrom() {
        return loadedFrom;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
