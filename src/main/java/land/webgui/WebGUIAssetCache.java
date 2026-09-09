package land.webgui;

import land.webgui.server.WebviewAssetStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The client's copy of the pages a server ships.
 *
 * Files arrive over the game connection rather than from a web host, so a server owner
 * needs nothing but a folder to put an interface in front of their players. That choice
 * has one consequence worth stating: a fetch is a round trip through the Minecraft
 * connection, so it is slow the first time and free afterwards.
 *
 * Free afterwards because everything is addressed by hash. A file already on disk from
 * an earlier session — or from another server shipping the same library — is never asked
 * for again, and a changed file is simply a different name, so nothing is ever stale.
 */
public final class WebGUIAssetCache {

    /** Long enough for a slow connection, short enough that a broken server is not a hang. */
    private static final long FETCH_TIMEOUT_MS = 20_000;

    private static final Map<String, WebviewAssetStore.Asset> MANIFEST = new ConcurrentHashMap<>();
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
    private static volatile String revision = "";
    private static volatile Path cacheDir;

    private WebGUIAssetCache() {}

    /** One in-flight download, awaited by the thread serving the browser's request. */
    private static final class Pending {
        final CountDownLatch done = new CountDownLatch(1);
        byte[][] chunks;
        volatile boolean failed;
    }

    // --- state from the server ----------------------------------------------

    public static void setManifest(String newRevision, String manifest) {
        MANIFEST.clear();
        MANIFEST.putAll(WebviewAssetStore.parseManifest(manifest));
        revision = newRevision == null ? "" : newRevision;
        failAllPending();
        WebGUIMod.LOGGER.info("webgui: server ships {} page file(s), revision {}", MANIFEST.size(), revision);
    }

    /** Called on disconnect: another server's pages are not ours to serve. */
    public static void clear() {
        MANIFEST.clear();
        revision = "";
        failAllPending();
    }

    public static boolean isEmpty() {
        return MANIFEST.isEmpty();
    }

    /** How many files this server said it ships. */
    public static int size() {
        return MANIFEST.size();
    }

    public static String revision() {
        return revision;
    }

    public static WebviewAssetStore.Asset lookup(String path) {
        return MANIFEST.get(WebviewAssetStore.normalizeRequest(path));
    }

    // --- chunks in -----------------------------------------------------------

    /**
     * Takes one slice of a file the server is sending back.
     *
     * A {@code chunkCount} of zero is the server saying it has no such file. That still
     * has to wake the waiting thread, or the browser request never completes.
     */
    public static void onChunk(String path, int chunkIndex, int chunkCount, byte[] bytes) {
        WebGUIMod.LOGGER.debug("webgui: chunk {} {}/{} ({} bytes)", path, chunkIndex, chunkCount, bytes.length);
        Pending pending = PENDING.get(WebviewAssetStore.normalizeRequest(path));
        if (pending == null) {
            // Nobody is waiting: a late chunk after a timeout, or a server sending
            // something that was never asked for.
            return;
        }
        if (chunkCount <= 0) {
            pending.failed = true;
            pending.done.countDown();
            return;
        }
        synchronized (pending) {
            if (pending.chunks == null || pending.chunks.length != chunkCount) {
                pending.chunks = new byte[chunkCount][];
            }
            if (chunkIndex < 0 || chunkIndex >= chunkCount) {
                pending.failed = true;
                pending.done.countDown();
                return;
            }
            pending.chunks[chunkIndex] = bytes;
            for (byte[] chunk : pending.chunks) {
                if (chunk == null) {
                    return;
                }
            }
        }
        pending.done.countDown();
    }

    private static void failAllPending() {
        for (Pending pending : PENDING.values()) {
            pending.failed = true;
            pending.done.countDown();
        }
        PENDING.clear();
    }

    // --- the read the browser is waiting on ----------------------------------

    /**
     * The bytes for a path, from disk if they are already there and from the server if
     * not. Null when the server does not have it, or did not answer in time.
     *
     * Called on the local HTTP server's thread, never on the game's: it blocks.
     */
    public static byte[] fetch(String path) {
        String key = WebviewAssetStore.normalizeRequest(path);
        WebviewAssetStore.Asset asset = MANIFEST.get(key);
        if (asset == null) {
            return null;
        }

        byte[] cached = readFromDisk(asset.sha256(), asset.size());
        if (cached != null) {
            return cached;
        }

        Pending pending = new Pending();
        Pending existing = PENDING.putIfAbsent(key, pending);
        boolean weAsked = existing == null;
        if (!weAsked) {
            // Two browser requests for the same file race constantly - a page and its
            // stylesheet, say. The second waits on the first rather than asking again.
            pending = existing;
        } else {
            WebGUIMod.LOGGER.debug("webgui: asking the server for {}", key);
            WebGUIClient.requestAsset(key);
        }

        try {
            if (!pending.done.await(FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                WebGUIMod.LOGGER.warn("webgui: timed out fetching {} from the server", key);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (weAsked) {
                PENDING.remove(key, pending);
            }
        }

        if (pending.failed || pending.chunks == null) {
            return null;
        }

        byte[] joined = join(pending.chunks);
        String actual = WebviewAssetStore.sha256(joined);
        if (!actual.equals(asset.sha256())) {
            // The manifest is the only thing saying what this file should be. Writing
            // mismatched bytes under that hash would poison the cache for every future
            // session, including on other servers shipping the same file.
            WebGUIMod.LOGGER.warn("webgui: {} did not match its hash; discarding", key);
            return null;
        }
        writeToDisk(actual, joined);
        return joined;
    }

    private static byte[] join(byte[][] chunks) {
        int total = 0;
        for (byte[] chunk : chunks) {
            total += chunk.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, out, offset, chunk.length);
            offset += chunk.length;
        }
        return out;
    }

    // --- disk ----------------------------------------------------------------

    /** Where files live between sessions. Set once, from the client's game directory. */
    public static void useCacheDir(Path dir) {
        cacheDir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            WebGUIMod.LOGGER.warn("webgui: no page cache at {} ({}); files will be refetched each time",
                    dir, e.getMessage());
            cacheDir = null;
        }
    }

    private static Path fileFor(String sha256) {
        Path dir = cacheDir;
        if (dir == null || sha256 == null || sha256.length() != 64 || !sha256.matches("[0-9a-f]{64}")) {
            return null;
        }
        // Two levels of fan-out: a busy client can accumulate thousands of files, and
        // some filesystems get slow long before that in one directory.
        return dir.resolve(sha256.substring(0, 2)).resolve(sha256);
    }

    private static byte[] readFromDisk(String sha256, long expectedSize) {
        Path file = fileFor(sha256);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length != expectedSize || !WebviewAssetStore.sha256(bytes).equals(sha256)) {
                // A truncated write from a previous crash. Drop it and refetch.
                Files.deleteIfExists(file);
                return null;
            }
            return bytes;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeToDisk(String sha256, byte[] bytes) {
        Path file = fileFor(sha256);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            // Written aside and moved into place: a crash mid-write would otherwise
            // leave a short file under a hash that says it is complete.
            Path temp = file.resolveSibling(sha256 + ".part");
            Files.write(temp, bytes);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            WebGUIMod.LOGGER.debug("webgui: could not cache {}: {}", sha256, e.getMessage());
        }
    }
}
