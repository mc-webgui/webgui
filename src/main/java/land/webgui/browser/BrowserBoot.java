package land.webgui.browser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import space.webgui.wmc.api.WebRuntime;
import space.webgui.wmc.core.CefRuntime;
import space.webgui.wmc.core.RuntimeSettings;

/**
 * Starts the browser engine, with what this mod wants from it.
 *
 * <p>Everything here used to be somebody else's decision. The engine is ours now, so the
 * flags are settings rather than a mixin into a library, and where a download lands is a
 * value rather than a callback that had to fight another mod's for the right to answer.
 */
public final class BrowserBoot {

    /** Where a page's downloads go, kept as it was when Rinku's handler chose it. */
    private static final String DOWNLOADS = "webgui-downloads";

    /**
     * What Chromium is told beyond its defaults.
     *
     * <p>These used to be injected by a mixin into Rinku's start-up, because a library that
     * owns the command line and does not expose it leaves no other way in. They raster on the
     * GPU even though the browser draws off-screen, and stop the page being paced by the
     * monitor's refresh — which it must not be, since nothing here is on a monitor.
     */
    private static final List<String> CHROMIUM_FLAGS = List.of(
            "--enable-gpu-rasterization",
            "--enable-oop-rasterization",
            "--disable-gpu-vsync");

    private static volatile boolean started;

    private BrowserBoot() {
    }

    /**
     * Configures the engine and asks it to come up.
     *
     * <p>Returns at once. Nothing is downloaded and no process is started until the first
     * page is opened, so calling this during start-up costs a few objects.
     */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;

        // Absolute, deliberately. Chromium is given these paths — the cache and where a
        // download lands — and it does not resolve a relative one against the JVM's working
        // directory. A download then vanishes: the folder is made here, the file is written
        // somewhere else or not at all, and nothing reports an error.
        Path gameDirectory = Paths.get("").toAbsolutePath().normalize();
        RuntimeSettings settings = RuntimeSettings.defaults()
                .gameDirectory(gameDirectory)
                .extraArguments(CHROMIUM_FLAGS);

        // Read here rather than waited for: this runs before the client finishes starting,
        // and Chromium takes its debugging port once, when it starts. Loading twice is
        // harmless — it re-reads the same file.
        land.webgui.WebGUIClientConfig.load();

        // Both are read once, here, because Chromium takes them when it starts. The settings
        // screen says so rather than letting a player wonder why nothing changed.
        settings = settings
                .allowAutoplay(land.webgui.WebGUIClientConfig.allowPageAutoplay())
                .downloads(RuntimeSettings.Downloads.defaults()
                        .into(gameDirectory.resolve(DOWNLOADS))
                        .asking(land.webgui.WebGUIClientConfig.askWhereToSaveDownloads()));

        // Set rather than read. With Rinku this port could only be reported, because the one
        // command line that mattered was fixed before any mod ran; here it is ours to choose —
        // so the setting in the mod's own screen is enough and no launch argument is needed.
        //
        // Off unless asked for, and that is not timidity: the protocol has no authentication,
        // so anything able to reach the port can read and drive every page the game has open.
        int port = land.webgui.WebGUIRemoteDebugging.port();
        if (port <= 0 && land.webgui.WebGUIClientConfig.devTools()) {
            port = land.webgui.WebGUIRemoteDebugging.SUGGESTED_PORT;
        }
        if (port > 0) {
            settings = settings.remoteDebuggingPort(port);
            land.webgui.WebGUIRemoteDebugging.started(port);
            land.webgui.WebGUIMod.LOGGER.info(
                    "webgui: the page inspector is available on http://127.0.0.1:{}", port);
        }

        CefRuntime.configure(settings);
        WebRuntime.get().whenReady().whenComplete((ignored, failure) -> {
            if (failure != null) {
                land.webgui.WebGUIMod.LOGGER.error(
                        "webgui: the browser engine did not start — web GUI will not work", failure);
            }
        });
    }
}
