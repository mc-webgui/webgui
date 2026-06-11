package land.webgui.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import land.webgui.WebGUIMod;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//? } else {
/*import net.neoforged.fml.loading.FMLPaths;*/
//? }

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public final class WebviewServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static Path configPath() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("server.json");
        //? } else {
        /*return FMLPaths.CONFIGDIR.get().resolve("webgui").resolve("server.json");*/
        //? }
    }

    private Boolean enableTokens;
    private int tokenTtlSeconds = 900;
    private String queryParamName = "webgui_token";
    private String tokenSecretBase64 = "";

    private Boolean autoHudOnJoin;
    private String autoHudUrl = "";

    private String mainMenuUrl = "";

    // {"version":"1.2.3"} or GitHub Releases {"tag_name":"v1.2.3","html_url":"..."}; empty = disabled
    private String updateCheckUrl = "";

    private static WebviewServerConfig instance = new WebviewServerConfig();

    private WebviewServerConfig() {}

    private static Path examplePath() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("server.example.json");
        //? } else {
        /*return FMLPaths.CONFIGDIR.get().resolve("webgui").resolve("server.example.json");*/
        //? }
    }

    private static void writeExample() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("enableTokens", true);
            o.addProperty("tokenTtlSeconds", 900);
            o.addProperty("queryParamName", "webgui_token");
            o.addProperty("tokenSecretBase64", "<base64-encoded 32-byte secret — auto-generated in server.json>");

            o.addProperty("autoHudOnJoin", false);
            o.addProperty("autoHudUrl", "http://your-site.example/hud");

            o.addProperty("mainMenuUrl", "http://your-site.example/menu");

            o.addProperty("updateCheckUrl", "");

            String json = GSON.toJson(o);
            Files.writeString(examplePath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            WebGUIMod.LOGGER.warn("webgui: could not write server.example.json", e);
        }
    }

    public static void load() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            writeExample();
            if (Files.isRegularFile(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                WebviewServerConfig read = GSON.fromJson(json, WebviewServerConfig.class);
                if (read != null) {
                    instance = read;
                }
            }
            if (instance.tokenSecretBase64 == null || instance.tokenSecretBase64.isBlank()) {
                byte[] secret = new byte[32];
                RANDOM.nextBytes(secret);
                instance.tokenSecretBase64 = Base64.getEncoder().encodeToString(secret);
                save();
                WebGUIMod.LOGGER.info("Generated new webgui token secret (config/webgui/server.json). Copy the same secret to your web backend to verify tokens.");
            } else {
                WebGUIMod.LOGGER.info("Loaded webgui server config from {}", path);
            }
        } catch (IOException e) {
            WebGUIMod.LOGGER.error("Failed to load webgui server config", e);
        }
        instance.applyDefaultsAfterLoad();
    }

    private void applyDefaultsAfterLoad() {
        if (tokenTtlSeconds < 60) {
            tokenTtlSeconds = 900;
        }
        if (queryParamName == null || queryParamName.isBlank()) {
            queryParamName = "webgui_token";
        }
        if (autoHudUrl == null) {
            autoHudUrl = "";
        } else {
            autoHudUrl = autoHudUrl.trim();
        }
        if (mainMenuUrl == null) {
            mainMenuUrl = "";
        } else {
            mainMenuUrl = mainMenuUrl.trim();
        }
        if (updateCheckUrl == null) {
            updateCheckUrl = "";
        } else {
            updateCheckUrl = updateCheckUrl.trim();
        }
    }

    static Gson gson() {
        return GSON;
    }

    private static void save() throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
    }

    public static boolean enableTokens() {
        Boolean b = instance.enableTokens;
        return b == null || b;
    }

    public static int tokenTtlSeconds() {
        return Math.max(60, instance.tokenTtlSeconds);
    }

    public static String queryParamName() {
        String n = instance.queryParamName;
        return n == null || n.isBlank() ? "webgui_token" : n;
    }

    public static byte[] tokenSecretBytes() {
        try {
            return Base64.getDecoder().decode(instance.tokenSecretBase64.trim());
        } catch (IllegalArgumentException e) {
            WebGUIMod.LOGGER.error("Invalid tokenSecretBase64 in server.json");
            return new byte[0];
        }
    }

    public static boolean autoHudOnJoin() {
        Boolean v = instance.autoHudOnJoin;
        return v != null && v;
    }

    public static String autoHudUrl() {
        String u = instance.autoHudUrl;
        return u == null ? "" : u.trim();
    }

    public static String mainMenuUrl() {
        String u = instance.mainMenuUrl;
        return u == null ? "" : u.trim();
    }

    public static String updateCheckUrl() {
        String u = instance.updateCheckUrl;
        return u == null ? "" : u.trim();
    }

    /** Reloads server.json from disk. Returns a human-readable status line for command feedback. */
    public static String reload() {
        Path path = configPath();
        try {
            if (!Files.isRegularFile(path)) {
                return "server.json not found — using current settings (path: " + path + ")";
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            WebviewServerConfig read = GSON.fromJson(json, WebviewServerConfig.class);
            if (read == null) {
                return "server.json is empty — using current settings";
            }
            instance = read;
            instance.applyDefaultsAfterLoad();
            if (instance.tokenSecretBase64 == null || instance.tokenSecretBase64.isBlank()) {
                byte[] secret = new byte[32];
                RANDOM.nextBytes(secret);
                instance.tokenSecretBase64 = Base64.getEncoder().encodeToString(secret);
                save();
                WebGUIMod.LOGGER.info("webgui: generated new token secret during reload");
            }
            WebGUIMod.LOGGER.info("webgui: config reloaded from {}", path);
            return "WebGUI config reloaded from " + path.getFileName();
        } catch (IOException e) {
            WebGUIMod.LOGGER.error("webgui: failed to reload config", e);
            return "Reload failed: " + e.getMessage();
        }
    }
}
