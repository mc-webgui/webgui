package land.webgui.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import land.webgui.WebGUIMod;
import land.webgui.WebGUIVersion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//? if fabric {
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
//? } else {
/*import org.apache.maven.artifact.versioning.DefaultArtifactVersion;*/
//? }

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Asks whether a newer WebGUI exists, once, when the server starts.
 *
 * <p>Modrinth first: it knows which builds exist for this Minecraft version and this loader,
 * so it will not offer one that cannot be installed here. GitHub answers only what is newest
 * overall, so it is the fallback for when Modrinth cannot be reached.
 *
 * <p>The console notice is not optional. Telling operators in chat on join is, through
 * {@code updateNotifyInChat}.
 */
public final class WebGUIUpdateChecker {

    private static final String MODRINTH_PROJECT = "tDw1xQja";
    private static final String GITHUB_REPO = "mc-webgui/webgui";

    //? if fabric {
    private static final String LOADER = "fabric";
    //? } else {
    /*private static final String LOADER = "neoforge";
    *///? }

    /** What was found, or null while nothing newer is known. Read from the join thread. */
    private static volatile Update available;

    private WebGUIUpdateChecker() {}

    /** A version newer than the one running. */
    public record Update(String version, String url, String channel) {}

    public static void checkAsync() {
        String full = WebGUIVersion.current();
        String current = stripBuildMeta(full);
        if (current.isEmpty() || "unknown".equals(current)) {
            return;
        }
        String game = gameVersion(full);

        Thread t = new Thread(() -> {
            Update found = fromModrinth(current, game);
            if (found == null) {
                found = fromGitHub(current);
            }
            if (found == null) {
                return;
            }
            available = found;
            WebGUIMod.LOGGER.info("+------------------------------------------------");
            WebGUIMod.LOGGER.info("| WebGUI update available: {} -> {}{}",
                    current, found.version(), channelSuffix(found));
            if (!found.url().isEmpty()) {
                WebGUIMod.LOGGER.info("| Download: {}", found.url());
            }
            WebGUIMod.LOGGER.info("+------------------------------------------------");
        }, "webgui-update-check");
        t.setDaemon(true);
        t.start();
    }

    /** Says it to an operator who just joined, if the server was told to. */
    public static void notifyOnJoin(ServerPlayer player) {
        Update found = available;
        if (found == null || player == null || !WebviewServerConfig.updateNotifyInChat()) {
            return;
        }
        //? if >=1.21.5 {
        boolean operator = net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        //? } else {
        /*boolean operator = player.hasPermissions(2);
        *///? }
        if (!operator) {
            return;
        }
        player.sendSystemMessage(Component.literal("[WebGUI] Update available: "
                        + stripBuildMeta(WebGUIVersion.current()) + " -> " + found.version()
                        + channelSuffix(found))
                .withStyle(ChatFormatting.YELLOW));
        if (!found.url().isEmpty()) {
            player.sendSystemMessage(Component.literal("[WebGUI] " + found.url())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Filtered by loader and game version, so anything that comes back can be installed here.
    private static Update fromModrinth(String current, String game) {
        if (game.isEmpty()) {
            return null;
        }
        String url = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT + "/version"
                + "?loaders=" + encode("[\"" + LOADER + "\"]")
                + "&game_versions=" + encode("[\"" + game + "\"]");
        String body = get(url);
        if (body == null) {
            return null;
        }
        try {
            JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
            Update best = null;
            for (JsonElement element : versions) {
                JsonObject o = element.getAsJsonObject();
                String number = stripBuildMeta(text(o, "version_number"));
                if (number.isEmpty() || !isNewer(number, current)) {
                    continue;
                }
                if (best != null && !isNewer(number, best.version())) {
                    continue;
                }
                best = new Update(number, pageUrl(o), text(o, "version_type"));
            }
            return best;
        } catch (Exception malformed) {
            WebGUIMod.LOGGER.debug("webgui: update check: Modrinth answered unexpectedly: {}",
                    malformed.getMessage());
            return null;
        }
    }

    // Newest release overall, which may have no build for this Minecraft version. Only reached
    // when Modrinth is unavailable, where an imprecise answer beats none.
    private static Update fromGitHub(String current) {
        String body = get("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
        if (body == null) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String number = stripBuildMeta(text(o, "tag_name"));
            if (number.isEmpty() || !isNewer(number, current)) {
                return null;
            }
            return new Update(number, text(o, "html_url"), "release");
        } catch (Exception malformed) {
            WebGUIMod.LOGGER.debug("webgui: update check: GitHub answered unexpectedly: {}",
                    malformed.getMessage());
            return null;
        }
    }

    private static String get(String url) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "WebGUI-Mod/" + WebGUIVersion.current())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                WebGUIMod.LOGGER.debug("webgui: update check: HTTP {} from {}", response.statusCode(), url);
                return null;
            }
            return response.body();
        } catch (Exception unreachable) {
            WebGUIMod.LOGGER.debug("webgui: update check failed: {}", unreachable.getMessage());
            return null;
        }
    }

    private static boolean isNewer(String remote, String local) {
        //? if fabric {
        try {
            return SemanticVersion.parse(remote).compareTo(SemanticVersion.parse(local)) > 0;
        } catch (VersionParsingException unparseable) {
            WebGUIMod.LOGGER.debug("webgui: update check: cannot compare {} with {}", remote, local);
            return false;
        }
        //? } else {
        /*return new DefaultArtifactVersion(remote).compareTo(new DefaultArtifactVersion(local)) > 0;
        *///? }
    }

    private static String channelSuffix(Update found) {
        return "release".equals(found.channel()) || found.channel().isEmpty()
                ? "" : " (" + found.channel() + ")";
    }

    private static String pageUrl(JsonObject version) {
        String id = text(version, "id");
        return id.isEmpty()
                ? "https://modrinth.com/mod/webgui/versions"
                : "https://modrinth.com/mod/webgui/version/" + id;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull()
                ? o.get(key).getAsString().trim().replaceFirst("^[vV]", "")
                : "";
    }

    /** This mod reports {@code 2.0.0+mc26.3}; Modrinth adds the loader after that. */
    private static String stripBuildMeta(String version) {
        String v = version == null ? "" : version.trim().replaceFirst("^[vV]", "");
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    /** The Minecraft version out of our own, which carries it as {@code +mc26.3}. */
    private static String gameVersion(String full) {
        if (full == null) {
            return "";
        }
        int mc = full.indexOf("+mc");
        if (mc < 0) {
            return "";
        }
        String rest = full.substring(mc + 3);
        int dash = rest.indexOf('-');
        return dash >= 0 ? rest.substring(0, dash) : rest;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
