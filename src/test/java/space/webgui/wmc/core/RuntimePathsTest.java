package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimePathsTest {

    private static RuntimePaths.Environment of(Map<String, String> values) {
        return values::get;
    }

    @Test
    void theEnvironmentOverridesEverythingForOfflineMachines() {
        Path root = RuntimePaths.defaultRuntimeRoot(
                of(Map.of(RuntimePaths.RUNTIME_DIR_ENV, "/opt/wmc")),
                of(Map.of("user.home", "/home/someone", "os.name", "Linux")));

        assertEquals(Path.of("/opt/wmc"), root);
    }

    @Test
    void fallsBackToTheHomeDirectory() {
        Path root = RuntimePaths.defaultRuntimeRoot(
                of(Map.of()),
                of(Map.of("user.home", "/home/someone", "os.name", "Linux")));

        assertEquals(Path.of("/home/someone/.wmc/runtime"), root);
    }

    @Test
    void windowsUsesLocalAppDataWhereSuchThingsBelong() {
        Path root = RuntimePaths.defaultRuntimeRoot(
                of(Map.of("LOCALAPPDATA", "C:\\Users\\someone\\AppData\\Local")),
                of(Map.of("user.home", "C:\\Users\\someone", "os.name", "Windows 11")));

        assertTrue(root.toString().contains("AppData"), root.toString());
        assertTrue(root.endsWith(Path.of("wmc", "runtime")), root.toString());
    }

    @Test
    void theCacheLivesWithTheGameAndTheRuntimeDoesNot(@TempDir Path game) {
        RuntimePaths paths = RuntimePaths.standard(game);

        // Chromium locks its cache, so two instances cannot share one. The runtime is
        // hundreds of megabytes and identical everywhere, so they must.
        assertTrue(paths.cacheRoot().startsWith(game.toAbsolutePath()));
        assertTrue(!paths.runtimeRoot().startsWith(game.toAbsolutePath()));
    }
}
