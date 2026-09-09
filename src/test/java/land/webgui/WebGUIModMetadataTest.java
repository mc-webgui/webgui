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

    /**
     * The Mods list is where a player looks to see what they installed and who wrote it,
     * and a declared icon that is not in the jar shows as a blank tile. Both loaders read
     * these from different files, so it is easy to fix one and forget the other.
     */
    @Test
    void declaresAuthorshipAndAnIconThatIsActuallyShipped() throws Exception {
        String fabric = findOurs("fabric.mod.json", "land.webgui.WebGUIMod");
        String neoforge = findOurs("META-INF/neoforge.mods.toml", "modId = \"" + MOD_ID + "\"");
        Assumptions.assumeTrue(fabric != null || neoforge != null,
                "our loader metadata is not exposed on the dev/test classpath — skipping");

        if (fabric != null) {
            assertContains(fabric, "KoSHeroff", "fabric.mod.json names the author");
            assertContains(fabric, "icon-128.png", "declares a 128px icon");
            assertContains(fabric, "https://webgui.space", "links to the homepage");
        }
        if (neoforge != null) {
            assertContains(neoforge, "authors = \"KoSHeroff\"", "neoforge.mods.toml names the author");
            assertContains(neoforge, "logoFile = \"icon-256.png\"", "declares a logo file");
            assertContains(neoforge, "displayURL", "links to the homepage");
            assertContains(neoforge, "issueTrackerURL", "links to the issue tracker");
        }

        for (String icon : new String[] { "/icon-128.png", "/icon-256.png" }) {
            try (InputStream in = getClass().getResourceAsStream(icon)) {
                assertNotNull(in, icon + " is declared in the metadata and must be in the jar");
                byte[] header = in.readNBytes(8);
                // A declared file that is not a PNG renders as a blank tile just the same.
                assertTrue(header.length == 8
                                && (header[0] & 0xFF) == 0x89 && header[1] == 'P'
                                && header[2] == 'N' && header[3] == 'G',
                        icon + " must be a PNG");
            }
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
