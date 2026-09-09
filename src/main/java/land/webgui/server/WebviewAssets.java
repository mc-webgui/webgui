package land.webgui.server;

import land.webgui.WebGUIMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The live view of {@code config/webgui/web/} for the running server.
 *
 * One store, rebuilt on start and on {@code /webgui reload}, so the operator edits
 * files and reloads rather than restarting. Reads are lock-free: the field is swapped
 * for a fully-built replacement, and a request in flight finishes against the store it
 * started with — the alternative is holding a lock on every asset request from every
 * player.
 */
public final class WebviewAssets {

    private static volatile WebviewAssetStore store = WebviewAssetStore.empty();

    private WebviewAssets() {}

    public static WebviewAssetStore store() {
        return store;
    }

    public static boolean enabled() {
        return WebviewServerConfig.serveBundledPages() && !store.isEmpty();
    }

    /**
     * Rescans the directory, creating it with a starter page the first time.
     *
     * The starter page matters more than it looks: the feature exists to remove the
     * "where do I even put this" step, and an empty folder answers that question no
     * better than no folder at all.
     */
    public static void reload() {
        if (!WebviewServerConfig.serveBundledPages()) {
            store = WebviewAssetStore.empty();
            WebGUIMod.LOGGER.info("webgui: bundled pages are off (serveBundledPages=false)");
            return;
        }

        Path root = WebviewServerConfig.bundledPagesRoot();
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                writeStarterPage(root);
            }
        } catch (IOException e) {
            WebGUIMod.LOGGER.warn("webgui: could not create {}: {}", root, e.getMessage());
        }

        WebviewAssetStore scanned = WebviewAssetStore.scan(root);
        store = scanned;

        for (String problem : scanned.problems()) {
            WebGUIMod.LOGGER.warn("webgui: skipped an asset — {}", problem);
        }
        if (scanned.isEmpty()) {
            WebGUIMod.LOGGER.info("webgui: no bundled pages in {}", root);
        } else {
            WebGUIMod.LOGGER.info("webgui: serving {} bundled page(s), {} KiB, revision {} from {}",
                    scanned.fileCount(), scanned.totalBytes() / 1024, scanned.revision(), root);
        }
    }

    /**
     * The chunks to answer one client's request with, or an empty list when there is
     * nothing to send.
     *
     * An empty list is a real answer, not a silent drop: the client has a browser
     * request waiting on this, and a page that hangs is worse to diagnose than one that
     * reports a missing file. Over-budget looks the same to the client and says why in
     * the log, which is the right way round — the operator can act on it, the page
     * cannot.
     */
    public static List<byte[]> chunksFor(UUID playerId, String playerName, String path) {
        WebviewAssetStore current = store;
        WebviewAssetStore.Asset asset = current.find(path);
        if (asset == null) {
            return List.of();
        }
        if (!WebviewRateLimiter.allow(playerId, playerName, WebviewRateLimiter.Bucket.ASSET_BYTES,
                asset.size(), WebviewServerConfig.assetBytesPerSecond())) {
            return List.of();
        }
        byte[] bytes;
        try {
            bytes = current.read(path);
        } catch (IOException e) {
            WebGUIMod.LOGGER.warn("webgui: could not read bundled page {}: {}", path, e.getMessage());
            return List.of();
        }
        if (bytes == null) {
            return List.of();
        }
        return split(bytes, land.webgui.WebviewPayloads.ASSET_CHUNK_BYTES);
    }

    /** Slices a file into transport-sized pieces. Always at least one, so empty files still arrive. */
    public static List<byte[]> split(byte[] data, int chunkSize) {
        if (data.length == 0) {
            return List.of(new byte[0]);
        }
        List<byte[]> chunks = new ArrayList<>((data.length + chunkSize - 1) / chunkSize);
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            int end = Math.min(offset + chunkSize, data.length);
            chunks.add(Arrays.copyOfRange(data, offset, end));
        }
        return chunks;
    }

    private static void writeStarterPage(Path root) throws IOException {
        Path index = root.resolve("index.html");
        if (Files.exists(index)) {
            return;
        }
        Files.writeString(index, """
                <!doctype html>
                <meta charset="utf-8">
                <title>WebGUI</title>
                <style>
                  body { margin:0; height:100vh; display:grid; place-items:center;
                         font-family: system-ui, sans-serif; background:#111318; color:#e8e8ea; }
                  code { background:#1e2129; padding:2px 6px; border-radius:5px; }
                </style>
                <div>
                  <h1>It works.</h1>
                  <p>This page came from <code>config/webgui/web/index.html</code> on the server.</p>
                  <p>Point <code>mainMenuUrl</code> at <code>webgui:/index.html</code> and edit away.</p>
                  <p id="who"></p>
                </div>
                <script>
                  addEventListener('webgui:client', e => {
                    document.getElementById('who').textContent = 'Hello, ' + e.detail.username + '.';
                  });
                </script>
                """);
        WebGUIMod.LOGGER.info("webgui: created a starter page at {}", index);
    }
}
