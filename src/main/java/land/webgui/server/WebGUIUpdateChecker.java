package land.webgui.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import land.webgui.WebGUIMod;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
//? } else {
/*import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;*/
//? }

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class WebGUIUpdateChecker {
    private WebGUIUpdateChecker() {}

    public static void checkAsync() {
        String url = WebviewServerConfig.updateCheckUrl();
        if (url.isEmpty()) {
            return;
        }
        //? if fabric {
        String current = FabricLoader.getInstance()
                .getModContainer(WebGUIMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        //? } else {
        /*String current = ModList.get()
                .getModContainerById(WebGUIMod.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("");*/
        //? }
        if (current.isEmpty()) {
            return;
        }

        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "WebGUI-Mod/" + current)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    WebGUIMod.LOGGER.debug("webgui: update check returned HTTP {}", resp.statusCode());
                    return;
                }
                parseAndLog(current, resp.body(), url);
            } catch (Exception e) {
                WebGUIMod.LOGGER.debug("webgui: update check failed: {}", e.getMessage());
            }
        }, "webgui-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static void parseAndLog(String current, String body, String checkUrl) {
        String remoteRaw;
        String downloadUrl = "";
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (o.has("version") && !o.get("version").isJsonNull()) {
                // {"version":"1.2.3","url":"..."}
                remoteRaw = o.get("version").getAsString();
                if (o.has("url") && !o.get("url").isJsonNull()) {
                    downloadUrl = o.get("url").getAsString();
                }
            } else if (o.has("tag_name") && !o.get("tag_name").isJsonNull()) {
                // GitHub Releases API: {"tag_name":"v1.2.3","html_url":"..."}
                remoteRaw = o.get("tag_name").getAsString();
                if (o.has("html_url") && !o.get("html_url").isJsonNull()) {
                    downloadUrl = o.get("html_url").getAsString();
                }
            } else {
                WebGUIMod.LOGGER.debug("webgui: update check: unrecognised response format");
                return;
            }
        } catch (Exception e) {
            WebGUIMod.LOGGER.debug("webgui: update check: could not parse response: {}", e.getMessage());
            return;
        }

        String remote = stripBuildMeta(remoteRaw.trim().replaceFirst("^[vV]", ""));
        String local  = stripBuildMeta(current.trim().replaceFirst("^[vV]", ""));

        boolean newer;
        //? if fabric {
        try {
            newer = SemanticVersion.parse(remote).compareTo(SemanticVersion.parse(local)) > 0;
        } catch (VersionParsingException e) {
            // fallback: simple string compare is not reliable, skip
            WebGUIMod.LOGGER.debug("webgui: update check: could not compare versions '{}' vs '{}': {}", remote, local, e.getMessage());
            return;
        }
        //? } else {
        /*newer = new DefaultArtifactVersion(remote).compareTo(new DefaultArtifactVersion(local)) > 0;*/
        //? }

        if (newer) {
            WebGUIMod.LOGGER.info("┌─────────────────────────────────────────────────");
            WebGUIMod.LOGGER.info("│ WebGUI update available: {} → {}", local, remote);
            if (!downloadUrl.isEmpty()) {
                WebGUIMod.LOGGER.info("│ Download: {}", downloadUrl);
            }
            WebGUIMod.LOGGER.info("└─────────────────────────────────────────────────");
        } else {
            WebGUIMod.LOGGER.debug("webgui: up to date ({} >= {})", local, remote);
        }
    }

    /** Strips the +mcX.Y.Z build-metadata suffix added by our Gradle build. */
    private static String stripBuildMeta(String version) {
        int plus = version.indexOf('+');
        return plus >= 0 ? version.substring(0, plus) : version;
    }
}
