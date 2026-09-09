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

import com.google.gson.JsonArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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

    // Page shown instead of the vanilla death screen. Empty = keep the vanilla screen.
    private String deathScreenUrl = "";

    // Serve pages from config/webgui/web/ over the players' own connection, so a server
    // needs no web host to use any of this. Directory relative to config/webgui.
    private Boolean serveBundledPages;
    private String bundledPagesDir = "web";

    // Refuse players whose client has no compatible WebGUI, instead of letting them in
    // with a warning. Off by default: WebGUI enhances a client, it does not gate entry.
    private Boolean requireClientMod;

    // Page events accepted from one player per second; the rest are dropped. 0 = no limit.
    private int pageEventsPerSecond = 20;

    // Bundled page bytes one player may pull per second. 0 = no limit.
    private int assetBytesPerSecond = 4 * 1024 * 1024;

    // {"version":"1.2.3"} or GitHub Releases {"tag_name":"v1.2.3","html_url":"..."}; empty = disabled
    private String updateCheckUrl = "";

    // Origins (scheme://host[:port]) whose pages may run commands as the player. Sent to the client
    // on join; the client rejects command requests from any other origin. Empty = no page may run commands.
    private List<String> trustedCommandOrigins = new ArrayList<>();

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

            o.addProperty("deathScreenUrl", "");

            o.addProperty("serveBundledPages", true);
            o.addProperty("bundledPagesDir", "web");

            o.addProperty("requireClientMod", false);
            o.addProperty("pageEventsPerSecond", 20);

            o.addProperty("updateCheckUrl", "");

            JsonArray origins = new JsonArray();
            origins.add("https://your-site.example");
            o.add("trustedCommandOrigins", origins);

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
        List<String> cleaned = new ArrayList<>();
        if (trustedCommandOrigins != null) {
            for (String o : trustedCommandOrigins) {
                if (o == null) continue;
                String t = o.trim();
                if (!t.isEmpty()) cleaned.add(t);
            }
        }
        trustedCommandOrigins = cleaned;
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

    /**
     * Page to show instead of the vanilla death screen; empty keeps the vanilla one.
     *
     * Opt-in on purpose: replacing the death screen takes away the player's only
     * way to respawn, so it happens only when a server explicitly asks for it.
     */
    public static String deathScreenUrl() {
        String u = instance.deathScreenUrl;
        return u == null ? "" : u.trim();
    }

    /**
     * Whether the server ships its own pages to clients over their game connection.
     *
     * On by default, and harmless when the directory is empty: it is the whole point of
     * the feature that a server owner needs nothing but a folder to get started.
     */
    public static boolean serveBundledPages() {
        Boolean v = instance.serveBundledPages;
        return v == null || v;
    }

    /** Where those pages live, resolved under {@code config/webgui}. */
    public static Path bundledPagesRoot() {
        String dir = instance.bundledPagesDir;
        if (dir == null || dir.isBlank()) {
            dir = "web";
        }
        Path base = configPath().getParent();
        Path resolved = base.resolve(dir).normalize();
        // A path escaping config/webgui would publish something the operator did not
        // mean to publish, and this value comes out of an editable file.
        return resolved.startsWith(base.normalize()) ? resolved : base.resolve("web");
    }

    /**
     * Whether a client without a compatible WebGUI is refused rather than warned.
     *
     * Default off. A server that opens pages the player cannot see still works — they
     * just miss the pages — so kicking them is a policy the owner opts into, not
     * something the mod decides for them.
     */
    public static boolean requireClientMod() {
        Boolean v = instance.requireClientMod;
        return v != null && v;
    }

    /** Page events accepted from one player per second; 0 means no limit. */
    public static int pageEventsPerSecond() {
        return Math.max(0, instance.pageEventsPerSecond);
    }

    /**
     * Bundled page bytes one player may pull per second; 0 means no limit.
     *
     * A page bundle is fetched once and then cached by hash, so this only has to be
     * generous enough for a first load — it exists to stop one client asking for the
     * same 8 MB file forty times a second.
     */
    public static int assetBytesPerSecond() {
        return Math.max(0, instance.assetBytesPerSecond);
    }

    public static String updateCheckUrl() {
        String u = instance.updateCheckUrl;
        return u == null ? "" : u.trim();
    }

    /** Origins whose pages may run commands as the player. Never null. */
    public static List<String> trustedCommandOrigins() {
        List<String> o = instance.trustedCommandOrigins;
        return o == null ? Collections.emptyList() : o;
    }

    /** Newline-joined trusted origins, for compact transport to the client. */
    public static String trustedCommandOriginsJoined() {
        return String.join("\n", trustedCommandOrigins());
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
            WebviewAssets.reload();
            WebGUIMod.LOGGER.info("webgui: config reloaded from {}", path);
            return "WebGUI config reloaded from " + path.getFileName();
        } catch (IOException e) {
            WebGUIMod.LOGGER.error("webgui: failed to reload config", e);
            return "Reload failed: " + e.getMessage();
        }
    }
}
