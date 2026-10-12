package space.webgui.wmc.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;

/**
 * Where the browser keeps its things.
 *
 * <p>Two directories, and the split is the whole point.
 *
 * <p>The <strong>runtime</strong> holds a few hundred megabytes of Chromium and is shared by
 * every instance and modpack belonging to one user: downloading it once per modpack would be
 * absurd, and it never differs between them.
 *
 * <p>The <strong>cache</strong> is per game directory, because Chromium locks it. Sharing one
 * between two running clients is precisely why other implementations leave the second client
 * without a browser.
 */
public final class RuntimePaths {

    /** Points the runtime at a directory prepared elsewhere, for machines with no internet. */
    public static final String RUNTIME_DIR_ENV = "WMC_RUNTIME_DIR";

    private final Path runtimeRoot;
    private final Path cacheRoot;

    RuntimePaths(Path runtimeRoot, Path cacheRoot) {
        this.runtimeRoot = Objects.requireNonNull(runtimeRoot, "runtimeRoot").toAbsolutePath();
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath();
    }

    /**
     * The usual arrangement.
     *
     * @param gameDirectory the directory this game instance runs in; the cache goes inside it
     */
    public static RuntimePaths standard(Path gameDirectory) {
        return new RuntimePaths(defaultRuntimeRoot(System::getenv, System::getProperty),
                gameDirectory.resolve(".wmc").resolve("cache"));
    }

    /** For tests, which should never touch a real user directory. */
    public static RuntimePaths at(Path runtimeRoot, Path cacheRoot) {
        return new RuntimePaths(runtimeRoot, cacheRoot);
    }

    static Path defaultRuntimeRoot(Environment environment, Environment properties) {
        String override = environment.get(RUNTIME_DIR_ENV);
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        String home = properties.get("user.home");
        Path base = home == null || home.isBlank() ? Paths.get(".") : Paths.get(home);
        String os = properties.get("os.name");
        if (os != null && os.toLowerCase(Locale.ROOT).contains("win")) {
            String appData = environment.get("LOCALAPPDATA");
            if (appData != null && !appData.isBlank()) {
                base = Paths.get(appData);
                return base.resolve("wmc").resolve("runtime");
            }
        }
        return base.resolve(".wmc").resolve("runtime");
    }

    /** Where a particular build of Chromium lives once installed. */
    public Path runtimeFor(String cefVersion, String platform) {
        return runtimeRoot.resolve(cefVersion).resolve(platform);
    }

    public Path runtimeRoot() {
        return runtimeRoot;
    }

    public Path cacheRoot() {
        return cacheRoot;
    }

    /** Indirection over the environment so the defaults can be tested without one. */
    @FunctionalInterface
    interface Environment {
        String get(String name);
    }
}
