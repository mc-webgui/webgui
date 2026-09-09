package land.webgui.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server's own web pages, read off disk and held ready to hand to clients.
 *
 * The point is to remove the one thing standing between a server owner and any of
 * this: today they need a domain, a host and a deploy pipeline before they can change
 * a button. Here they drop files in {@code config/webgui/web/} and the mod ships them
 * down the connection the player is already on — no port to open, nothing to expose,
 * and it works behind NAT like everything else the server sends.
 *
 * Content is addressed by hash so a client that already has a file never fetches it
 * again, and so a page can be cached hard without the usual "did it update?" question.
 *
 * This class is deliberately free of Minecraft types: it is the part worth unit
 * testing, and the network side is thin enough to review by eye.
 */
public final class WebviewAssetStore {

    /** Anything larger is almost certainly not a page asset, and would stall the connection. */
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    /** A cap on the whole directory, so one bad copy-paste cannot pin the server's heap. */
    public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    public static final int MAX_FILES = 2000;
    /** Deep trees are a symptom of pointing this at the wrong directory. */
    public static final int MAX_DEPTH = 12;
    /**
     * The manifest goes to every joining player in one packet, and a custom payload has
     * a hard ceiling of 1 MiB. Half of that leaves room for the packet's own framing and
     * still fits far more files than a sane page bundle has.
     */
    public static final int MAX_MANIFEST_BYTES = 512 * 1024;

    /** One file, as the client will see it. */
    public record Asset(String path, String sha256, long size, String contentType) {}

    private final Map<String, Asset> byPath;
    private final Path root;
    private final long totalBytes;
    private final List<String> problems;

    private WebviewAssetStore(Path root, Map<String, Asset> byPath, long totalBytes, List<String> problems) {
        this.root = root;
        this.byPath = byPath;
        this.totalBytes = totalBytes;
        this.problems = problems;
    }

    /** An empty store, for when the feature is off or the directory does not exist. */
    public static WebviewAssetStore empty() {
        return new WebviewAssetStore(null, Collections.emptyMap(), 0L, List.of());
    }

    public boolean isEmpty() {
        return byPath.isEmpty();
    }

    public int fileCount() {
        return byPath.size();
    }

    public long totalBytes() {
        return totalBytes;
    }

    public Path root() {
        return root;
    }

    /** Everything that was skipped and why, for the operator to see in the log. */
    public List<String> problems() {
        return problems;
    }

    public Map<String, Asset> assets() {
        return Collections.unmodifiableMap(byPath);
    }

    public Asset find(String path) {
        return byPath.get(normalizeRequest(path));
    }

    /**
     * Reads a file back, checking it still matches what was scanned.
     *
     * Re-resolving from the stored relative path rather than keeping handles open: the
     * directory is the operator's to edit while the server runs, and serving a file
     * that has changed under us would hand the client bytes whose hash is a lie.
     */
    public byte[] read(String path) throws IOException {
        Asset asset = find(path);
        if (asset == null || root == null) {
            return null;
        }
        Path file = resolveInside(root, asset.path());
        if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length != asset.size() || !sha256(bytes).equals(asset.sha256())) {
            // Edited since the scan. The client is asking for a hash we no longer have,
            // so there is nothing honest to return; a reload will pick up the new one.
            return null;
        }
        return bytes;
    }

    /**
     * Walks the directory and records every file that passes the limits.
     *
     * Never throws for a bad tree: a server should still start when someone points this
     * at the wrong place. Whatever was rejected comes back in {@link #problems()}.
     */
    public static WebviewAssetStore scan(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return empty();
        }

        Map<String, Asset> found = new LinkedHashMap<>();
        List<String> problems = new java.util.ArrayList<>();
        long[] total = {0L};

        try {
            Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_DEPTH,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (found.size() >= MAX_FILES) {
                                problems.add("stopped at " + MAX_FILES + " files");
                                return FileVisitResult.TERMINATE;
                            }
                            // Symlinks are not followed by the walk, but a link to a file
                            // still shows up as a regular file to attrs; check explicitly so
                            // a link cannot reach outside the directory being published.
                            if (!attrs.isRegularFile() || Files.isSymbolicLink(file)) {
                                return FileVisitResult.CONTINUE;
                            }
                            String rel = relative(root, file);
                            if (rel == null) {
                                problems.add(file + ": outside the web root");
                                return FileVisitResult.CONTINUE;
                            }
                            if (attrs.size() > MAX_FILE_BYTES) {
                                problems.add(rel + ": " + attrs.size() + " bytes, over the " + MAX_FILE_BYTES + " limit");
                                return FileVisitResult.CONTINUE;
                            }
                            if (total[0] + attrs.size() > MAX_TOTAL_BYTES) {
                                problems.add("stopped at " + MAX_TOTAL_BYTES + " bytes total");
                                return FileVisitResult.TERMINATE;
                            }
                            try {
                                byte[] bytes = Files.readAllBytes(file);
                                found.put(rel, new Asset(rel, sha256(bytes), bytes.length, contentTypeFor(rel)));
                                total[0] += bytes.length;
                            } catch (IOException e) {
                                problems.add(rel + ": " + e.getMessage());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException e) {
                            problems.add(file + ": " + e.getMessage());
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            problems.add(root + ": " + e.getMessage());
        }

        WebviewAssetStore store = new WebviewAssetStore(root, found, total[0], List.copyOf(problems));

        // Checked after the walk rather than during it: the manifest's size depends on
        // every path in it, so there is no running total to compare against, and a
        // half-published set would be worse than a clear refusal.
        int manifestBytes = store.toManifest().getBytes(StandardCharsets.UTF_8).length;
        if (manifestBytes > MAX_MANIFEST_BYTES) {
            List<String> withReason = new java.util.ArrayList<>(problems);
            withReason.add("the file list is " + manifestBytes + " bytes, over the "
                    + MAX_MANIFEST_BYTES + " limit for one packet — serve fewer files, or host them yourself");
            return new WebviewAssetStore(root, Collections.emptyMap(), 0L, List.copyOf(withReason));
        }
        return store;
    }

    // --- paths --------------------------------------------------------------

    /**
     * The path a client asks for, reduced to the form used as a key.
     *
     * Leading slashes go, backslashes become slashes, and {@code .} / {@code ..}
     * segments are resolved away — a request is a lookup key here, never something
     * handed to the filesystem, but normalising means {@code /a/../b} and {@code b}
     * cannot be two different cache entries for one file.
     */
    public static String normalizeRequest(String requested) {
        if (requested == null) {
            return "";
        }
        String s = requested.replace('\\', '/');
        int query = s.indexOf('?');
        if (query >= 0) s = s.substring(0, query);
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);

        java.util.ArrayDeque<String> parts = new java.util.ArrayDeque<>();
        for (String part : s.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                parts.pollLast();
                continue;
            }
            parts.addLast(part);
        }
        return String.join("/", parts);
    }

    /** The file's path relative to the root, or null if it somehow escaped. */
    private static String relative(Path root, Path file) {
        Path r = root.toAbsolutePath().normalize();
        Path f = file.toAbsolutePath().normalize();
        if (!f.startsWith(r)) {
            return null;
        }
        return r.relativize(f).toString().replace('\\', '/');
    }

    /** Resolves a stored relative path back to a file, refusing anything outside the root. */
    private static Path resolveInside(Path root, String rel) {
        Path r = root.toAbsolutePath().normalize();
        Path f = r.resolve(rel).normalize();
        return f.startsWith(r) ? f : null;
    }

    // --- content ------------------------------------------------------------

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }

    /**
     * A content type per extension, from a fixed table.
     *
     * Not {@code Files.probeContentType}: it consults the OS, so the same file would be
     * served as {@code text/plain} on one machine and {@code text/javascript} on
     * another — and a wrong type on a module script is a blank page with a console
     * error, which is a miserable thing to debug through a game window.
     */
    public static String contentTypeFor(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int dot = p.lastIndexOf('.');
        String ext = dot < 0 ? "" : p.substring(dot + 1);
        return switch (ext) {
            case "html", "htm" -> "text/html; charset=utf-8";
            case "js", "mjs"   -> "text/javascript; charset=utf-8";
            case "css"         -> "text/css; charset=utf-8";
            case "json", "map" -> "application/json; charset=utf-8";
            case "svg"         -> "image/svg+xml";
            case "png"         -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif"         -> "image/gif";
            case "webp"        -> "image/webp";
            case "avif"        -> "image/avif";
            case "ico"         -> "image/x-icon";
            case "woff2"       -> "font/woff2";
            case "woff"        -> "font/woff";
            case "ttf"         -> "font/ttf";
            case "otf"         -> "font/otf";
            case "wasm"        -> "application/wasm";
            case "mp3"         -> "audio/mpeg";
            case "ogg", "oga"  -> "audio/ogg";
            case "wav"         -> "audio/wav";
            case "mp4"         -> "video/mp4";
            case "webm"        -> "video/webm";
            case "txt"         -> "text/plain; charset=utf-8";
            case "xml"         -> "application/xml; charset=utf-8";
            case "pdf"         -> "application/pdf";
            default            -> "application/octet-stream";
        };
    }

    /** The manifest sent to clients: one line per file, {@code path\thash\tsize\ttype}. */
    public String toManifest() {
        StringBuilder sb = new StringBuilder();
        for (Asset a : byPath.values()) {
            sb.append(a.path()).append('\t')
              .append(a.sha256()).append('\t')
              .append(a.size()).append('\t')
              .append(a.contentType()).append('\n');
        }
        return sb.toString();
    }

    /** Parses {@link #toManifest()} back, for the client side and for tests. */
    public static Map<String, Asset> parseManifest(String manifest) {
        Map<String, Asset> out = new LinkedHashMap<>();
        if (manifest == null || manifest.isBlank()) {
            return out;
        }
        for (String line : manifest.split("\n")) {
            if (line.isBlank()) continue;
            String[] f = line.split("\t", 4);
            if (f.length != 4) continue;
            long size;
            try {
                size = Long.parseLong(f[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            out.put(f[0], new Asset(f[0], f[1], size, f[3]));
        }
        return out;
    }

    /** A short fingerprint of the whole set, so a client can skip an unchanged manifest. */
    public String revision() {
        return sha256(toManifest().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }
}
