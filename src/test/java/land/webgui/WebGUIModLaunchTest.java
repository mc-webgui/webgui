package land.webgui;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Launch smoke test: boots Minecraft + the mod loader in-process and asserts the
 * WebGUI mod loads and its entrypoint class is reachable. Loader APIs are called
 * by reflection so the one source compiles under both Fabric and NeoForge.
 *
 * <p>Fabric runs it via {@code fabric-loader-junit} in the {@code launchTest}
 * task; NeoForge via moddev's {@code unitTest} in the standard {@code test} task.
 */
@Tag("launch")
class WebGUIModLaunchTest {

    private static final String MOD_ID = "webgui";

    private enum Loader { FABRIC, NEOFORGE }

    private static Loader detectLoader() {
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return Loader.FABRIC;
        } catch (ClassNotFoundException e) {
            return Loader.NEOFORGE;
        }
    }

    private static boolean isModLoaded(String id) throws Exception {
        if (detectLoader() == Loader.FABRIC) {
            Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = fl.getMethod("getInstance").invoke(null);
            return (boolean) fl.getMethod("isModLoaded", String.class).invoke(inst, id);
        }
        Class<?> ml = Class.forName("net.neoforged.fml.ModList");
        Object inst = ml.getMethod("get").invoke(null);
        return (boolean) ml.getMethod("isLoaded", String.class).invoke(inst, id);
    }

    /** Returns the mod id as reported by the loader's own mod container/metadata. */
    private static String loadedModId(String id) throws Exception {
        if (detectLoader() == Loader.FABRIC) {
            Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = fl.getMethod("getInstance").invoke(null);
            @SuppressWarnings("unchecked")
            Optional<Object> container = (Optional<Object>) fl.getMethod("getModContainer", String.class).invoke(inst, id);
            assertTrue(container.isPresent(), "mod container should be present");
            Class<?> containerCls = Class.forName("net.fabricmc.loader.api.ModContainer");
            Class<?> metadataCls = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");
            Object metadata = containerCls.getMethod("getMetadata").invoke(container.get());
            return (String) metadataCls.getMethod("getId").invoke(metadata);
        }
        Class<?> ml = Class.forName("net.neoforged.fml.ModList");
        Object inst = ml.getMethod("get").invoke(null);
        @SuppressWarnings("unchecked")
        Optional<Object> container = (Optional<Object>) ml.getMethod("getModContainerById", String.class).invoke(inst, id);
        assertTrue(container.isPresent(), "mod container should be present");
        Class<?> containerCls = Class.forName("net.neoforged.fml.ModContainer");
        Object modInfo = containerCls.getMethod("getModInfo").invoke(container.get());
        Class<?> modInfoCls = Class.forName("net.neoforged.neoforgespi.language.IModInfo");
        return (String) modInfoCls.getMethod("getModId").invoke(modInfo);
    }

    @Test
    void minecraftBootsWithTheModLoaded() throws Exception {
        assertTrue(isModLoaded(MOD_ID),
                "WebGUI mod ('" + MOD_ID + "') should be loaded after launch on " + detectLoader());
    }

    @Test
    void modContainerHasExpectedMetadata() throws Exception {
        assertEquals(MOD_ID, loadedModId(MOD_ID));
    }

    @Test
    void mainEntrypointClassIsLoadable() throws Exception {
        Class<?> entry = Class.forName("land.webgui.WebGUIMod");
        assertEquals(MOD_ID, entry.getField("MOD_ID").get(null));
    }
}
