package land.webgui;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the mod is correctly declared for the active Stonecutter loader —
 * {@code fabric.mod.json} or {@code neoforge.mods.toml} — without booting the
 * game (so it also covers NeoForge, where the launch test can't run).
 *
 * <p>Many classpath jars ship a file at the same path, so we scan all matching
 * resources for ours; if loom strips ours from the test classpath, the checks
 * are skipped (the Fabric launch test already exercises loader parsing).
 */
class WebGUIModMetadataTest {

    private static final String MOD_ID = "webgui";

    /** Reads every classpath resource at {@code path} and returns the first whose content contains {@code marker}. */
    private String findOurs(String path, String marker) throws Exception {
        Enumeration<URL> urls = getClass().getClassLoader().getResources(path);
        while (urls.hasMoreElements()) {
            String content = read(urls.nextElement());
            if (content != null && content.contains(marker)) {
                return content;
            }
        }
        return null;
    }

    private static String read(URL url) throws Exception {
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String rootResource(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertContains(String haystack, String needle, String what) {
        assertTrue(haystack.contains(needle), what + " (expected to find: " + needle + ")");
    }

    @Test
    void declaresModAndEntrypointsForActiveLoader() throws Exception {
        String fabric = findOurs("fabric.mod.json", "land.webgui.WebGUIMod");
        String neoforge = findOurs("META-INF/neoforge.mods.toml", "modId = \"" + MOD_ID + "\"");
        Assumptions.assumeTrue(fabric != null || neoforge != null,
                "our loader metadata is not exposed on the dev/test classpath — skipping "
                        + "(the Fabric launch test covers loader parsing directly)");

        if (fabric != null) {
            assertContains(fabric, "\"webgui\"", "fabric.mod.json declares mod id webgui");
            assertContains(fabric, "land.webgui.WebGUIMod", "declares the main entrypoint");
            assertContains(fabric, "land.webgui.WebGUIClient", "declares the client entrypoint");
            assertContains(fabric, "webgui.mixins.json", "registers the mixin config");
            assertContains(fabric, "fabric-api", "depends on fabric-api");
            assertTrue(!fabric.contains("${version}"),
                    "version placeholder should be expanded by processResources");
        }
        if (neoforge != null) {
            assertContains(neoforge, "javafml", "uses the javafml loader");
            assertContains(neoforge, "webgui.mixins.json", "registers the mixin config");
            assertContains(neoforge, "modId = \"neoforge\"", "depends on neoforge");
            assertContains(neoforge, "modId = \"minecraft\"", "depends on minecraft");
            assertTrue(!neoforge.contains("${version}"),
                    "version placeholder should be expanded by processResources");
        }
    }

    @Test
    void mixinConfigIsPresentAndPointsAtTheMixinPackage() throws Exception {
        // webgui.mixins.json is uniquely named to this mod, so the root lookup is unambiguous.
        String mixins = rootResource("/webgui.mixins.json");
        assertNotNull(mixins, "webgui.mixins.json must be on the classpath");
        assertContains(mixins, "\"package\": \"land.webgui.mixin\"", "mixin package is set");
        assertContains(mixins, "\"required\": true", "mixin config is required");
    }
}
