package land.webgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//? } else {
/*import net.neoforged.fml.loading.FMLPaths;*/
//? }

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings that belong to the player, not the server.
 *
 * Written on change rather than on exit: the choice here is often made to diagnose a
 * crash, and a crash must not lose it.
 */
public final class WebGUIClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Off by default: a developer's tool, and it makes the log noisier. */
    private boolean devTools = false;

    private static WebGUIClientConfig instance = new WebGUIClientConfig();

    private WebGUIClientConfig() {}

    private static Path configPath() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("client.json");
        //? } else {
        /*return FMLPaths.CONFIGDIR.get().resolve("webgui").resolve("client.json");*/
        //? }
    }

    public static void load() {
        Path path = configPath();
        try {
            if (Files.isRegularFile(path)) {
                WebGUIClientConfig read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                        WebGUIClientConfig.class);
                if (read != null) {
                    instance = read;
                }
            }
        } catch (IOException | RuntimeException e) {
            // A typo in a hand-edited file must not stop the game starting.
            WebGUIMod.LOGGER.warn("webgui: could not read {} - using defaults ({})", path, e.toString());
        }
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            WebGUIMod.LOGGER.warn("webgui: could not write {}: {}", path, e.toString());
        }
    }

    public static boolean devTools() {
        return instance.devTools;
    }

    public static void setDevTools(boolean value) {
        if (instance.devTools == value) {
            return;
        }
        instance.devTools = value;
        save();
    }
}
