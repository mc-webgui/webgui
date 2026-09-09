package land.webgui.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The asset store decides which bytes leave the server, so its path handling and its
 * limits are the parts worth testing directly. Touches no Minecraft classes.
 */
class WebviewAssetStoreTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    void scansFilesWithHashSizeAndType(@TempDir Path root) throws IOException {
        write(root.resolve("index.html"), "<h1>hi</h1>");
        write(root.resolve("assets/app.js"), "console.log(1)");

        WebviewAssetStore store = WebviewAssetStore.scan(root);

        assertEquals(2, store.fileCount());
        WebviewAssetStore.Asset page = store.find("index.html");
        assertNotNull(page);
        assertEquals("text/html; charset=utf-8", page.contentType());
        assertEquals(11, page.size());
        assertEquals(WebviewAssetStore.sha256("<h1>hi</h1>".getBytes(StandardCharsets.UTF_8)), page.sha256());
        assertEquals("text/javascript; charset=utf-8", store.find("assets/app.js").contentType());
    }

    @Test
    void nestedPathsUseForwardSlashesOnEveryPlatform(@TempDir Path root) throws IOException {
        write(root.resolve("a").resolve("b").resolve("c.css"), "body{}");

        WebviewAssetStore store = WebviewAssetStore.scan(root);

        assertNotNull(store.find("a/b/c.css"));
        // The same file requested the way a Windows path would spell it.
        assertNotNull(store.find("a\\b\\c.css"));
    }

    @Test
    void missingDirectoryIsAnEmptyStoreRatherThanAFailure(@TempDir Path root) {
        WebviewAssetStore store = WebviewAssetStore.scan(root.resolve("nope"));

        assertTrue(store.isEmpty());
        assertEquals(0, store.totalBytes());
    }

    // --- the part that decides which bytes leave the machine -----------------

    @Test
    void traversalCannotReachOutsideTheRoot(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("web");
        write(root.resolve("index.html"), "public");
        write(tmp.resolve("secret.txt"), "private");

        WebviewAssetStore store = WebviewAssetStore.scan(root);

        assertNull(store.find("../secret.txt"));
        assertNull(store.find("..\\secret.txt"));
        assertNull(store.find("a/../../secret.txt"));
        assertNull(store.read("../secret.txt"));
    }

    @Test
    void normalizeCollapsesEquivalentSpellingsOfOnePath() {
        assertEquals("a/b.js", WebviewAssetStore.normalizeRequest("/a/b.js"));
        assertEquals("a/b.js", WebviewAssetStore.normalizeRequest("./a/./b.js"));
        assertEquals("a/b.js", WebviewAssetStore.normalizeRequest("a//b.js"));
        assertEquals("a/b.js", WebviewAssetStore.normalizeRequest("x/../a/b.js"));
        assertEquals("a/b.js", WebviewAssetStore.normalizeRequest("a/b.js?v=2"));
        assertEquals("a/b.js", WebviewAssetStore.normalizeRequest("a/b.js#top"));
        // Climbing past the root leaves nothing rather than escaping.
        assertEquals("secret", WebviewAssetStore.normalizeRequest("../../secret"));
    }

    @Test
    void oversizedFilesAreSkippedAndReported(@TempDir Path root) throws IOException {
        Files.createDirectories(root);
        Files.write(root.resolve("huge.bin"), new byte[(int) WebviewAssetStore.MAX_FILE_BYTES + 1]);
        write(root.resolve("small.html"), "ok");

        WebviewAssetStore store = WebviewAssetStore.scan(root);

        assertNull(store.find("huge.bin"));
        assertNotNull(store.find("small.html"));
        assertTrue(store.problems().stream().anyMatch(p -> p.contains("huge.bin")),
                "the operator should be told what was skipped: " + store.problems());
    }

    // --- serving -------------------------------------------------------------

    @Test
    void readReturnsTheScannedBytes(@TempDir Path root) throws IOException {
        write(root.resolve("index.html"), "<h1>hi</h1>");

        WebviewAssetStore store = WebviewAssetStore.scan(root);

        assertEquals("<h1>hi</h1>", new String(store.read("index.html"), StandardCharsets.UTF_8));
        assertEquals("<h1>hi</h1>", new String(store.read("/index.html"), StandardCharsets.UTF_8));
    }

    @Test
    void readRefusesAFileEditedSinceTheScan(@TempDir Path root) throws IOException {
        Path page = root.resolve("index.html");
        write(page, "original");
        WebviewAssetStore store = WebviewAssetStore.scan(root);

        // The directory belongs to the operator and they may edit it while the server
        // runs. Handing back bytes whose hash no longer matches would make the client's
        // content-addressed cache hold a file under the wrong name, for good.
        write(page, "edited in place");

        assertNull(store.read("index.html"));
    }

    @Test
    void readOfAnUnknownPathIsNull(@TempDir Path root) throws IOException {
        write(root.resolve("index.html"), "hi");

        assertNull(WebviewAssetStore.scan(root).read("nope.html"));
    }

    // --- manifest ------------------------------------------------------------

    @Test
    void manifestRoundTrips(@TempDir Path root) throws IOException {
        write(root.resolve("index.html"), "<h1>hi</h1>");
        write(root.resolve("assets/app.js"), "console.log(1)");
        WebviewAssetStore store = WebviewAssetStore.scan(root);

        Map<String, WebviewAssetStore.Asset> parsed = WebviewAssetStore.parseManifest(store.toManifest());

        assertEquals(store.assets(), parsed);
    }

    @Test
    void revisionChangesWithContentAndNotWithARescan(@TempDir Path root) throws IOException {
        write(root.resolve("index.html"), "one");
        String before = WebviewAssetStore.scan(root).revision();

        assertEquals(before, WebviewAssetStore.scan(root).revision());

        write(root.resolve("index.html"), "two");
        assertFalse(before.equals(WebviewAssetStore.scan(root).revision()));
    }

    @Test
    void emptyManifestParsesToNothing() {
        assertTrue(WebviewAssetStore.parseManifest("").isEmpty());
        assertTrue(WebviewAssetStore.parseManifest(null).isEmpty());
        assertTrue(WebviewAssetStore.parseManifest("garbage without tabs\n").isEmpty());
    }

    @Test
    void contentTypesCoverTheThingsAPageActuallyLoads() {
        assertEquals("text/html; charset=utf-8", WebviewAssetStore.contentTypeFor("a.html"));
        assertEquals("text/javascript; charset=utf-8", WebviewAssetStore.contentTypeFor("a.mjs"));
        assertEquals("application/wasm", WebviewAssetStore.contentTypeFor("a.wasm"));
        assertEquals("font/woff2", WebviewAssetStore.contentTypeFor("a.WOFF2"));
        assertEquals("application/octet-stream", WebviewAssetStore.contentTypeFor("a.unknown"));
        assertEquals("application/octet-stream", WebviewAssetStore.contentTypeFor("noextension"));
    }
}
