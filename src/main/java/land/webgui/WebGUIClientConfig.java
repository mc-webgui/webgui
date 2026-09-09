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
 * Separate from {@code server.json} because nothing here is a server's business: it is
 * what one person wants their own client to do. Written on change rather than on exit, so
 * a crash cannot lose the choice that may well have been made to diagnose that crash.
 */
public final class WebGUIClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Forward what Chromium knows about the page into the game log.
     *
     * Off by default: it is a developer's tool, it makes the log noisier, and a player
     * running someone else's pages has no use for it.
     */
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
            // A hand-edited file with a typo in it must not stop the game from starting;
            // the defaults are all usable.
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
