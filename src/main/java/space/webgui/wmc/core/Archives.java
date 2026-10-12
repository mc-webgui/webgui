package space.webgui.wmc.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unpacks a Chromium bundle.
 *
 * <p>Two things here are not incidental.
 *
 * <p><strong>Entries are confined to the target.</strong> An archive entry naming
 * {@code ../../something} would otherwise write outside the directory being extracted into,
 * which is a way to overwrite arbitrary files on the machine. Every entry is resolved and
 * checked before anything is created.
 *
 * <p><strong>Mode bits are kept.</strong> A Chromium bundle contains helper executables, and
 * an extractor that drops the execute bit produces an install that looks complete and cannot
 * start. That failure is silent and confusing, and it is the reason tar is used rather than
 * zip on the platforms where modes matter.
 */
public final class Archives {

    private Archives() {
    }

    private static final byte[] GZIP_MAGIC = {(byte) 0x1f, (byte) 0x8b};
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};

    /**
     * Extracts a downloaded bundle into {@code target}, whichever of the two shapes it is.
     *
     * <p>Bundles come as a gzipped tar. The ones published to Maven Central come as that tar
     * inside a jar, because a jar is what a Maven repository will host — so the wrapper is
     * unwrapped here rather than everywhere a source is written.
     *
     * @return how many entries were written
     */
    public static int extractBundle(Path archive, Path target) throws IOException {
        if (startsWith(archive, ZIP_MAGIC)) {
            return extractTarGzInsideZip(archive, target);
        }
        if (!startsWith(archive, GZIP_MAGIC)) {
            throw new InstallException("the downloaded runtime is neither a gzipped tar nor a jar");
        }
        try (InputStream in = Files.newInputStream(archive)) {
            return extractTarGz(in, target);
        }
    }

    private static int extractTarGzInsideZip(Path archive, Path target) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry bundle = null;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".tar.gz")) {
                    if (bundle != null) {
                        throw new InstallException("the jar holds more than one bundle, so which one is"
                                + " the runtime is a guess: " + bundle.getName() + " and " + entry.getName());
                    }
                    bundle = entry;
                }
            }
            if (bundle == null) {
                throw new InstallException("the jar holds no bundle to extract");
            }
            try (InputStream in = zip.getInputStream(bundle)) {
                return extractTarGz(in, target);
            }
        }
    }

    private static boolean startsWith(Path file, byte[] magic) throws IOException {
        byte[] head = new byte[magic.length];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(head, 0, head.length) != head.length) {
                return false;
            }
        }
        return java.util.Arrays.equals(head, magic);
    }

    /**
     * Extracts a gzipped tar into {@code target}, which must already exist.
     *
     * @return how many entries were written
     */
    public static int extractTarGz(InputStream source, Path target) throws IOException {
        Path root = target.toRealPath();
        int written = 0;
        try (Tar tar = new Tar(new GZIPInputStream(new BufferedInputStream(source)))) {
            Tar.Entry entry;
            while ((entry = tar.next()) != null) {
                Path destination = resolveWithin(root, entry.name());
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else if (entry.isSymbolicLink()) {
                    writeSymlink(root, destination, entry.linkName());
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(tar, destination, StandardCopyOption.REPLACE_EXISTING);
                    applyMode(destination, entry.mode());
                }
                written++;
            }
        }
        return written;
    }

    /**
     * Resolves an entry name inside {@code root}, refusing anything that escapes it.
     *
     * @throws InstallException if the entry would be written outside the target
     */
    static Path resolveWithin(Path root, String entryName) {
        Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new InstallException("archive entry would be written outside the target: " + entryName);
        }
        return resolved;
    }

    private static void writeSymlink(Path root, Path destination, String linkTarget) throws IOException {
        // A link is as good as an entry for escaping the target, so where it points is
        // checked the same way. Frameworks on macOS are full of them, so they cannot simply
        // be skipped.
        resolveWithin(root, root.relativize(destination.getParent()).resolve(linkTarget).toString());
        Files.createDirectories(destination.getParent());
        Files.deleteIfExists(destination);
        try {
            Files.createSymbolicLink(destination, destination.getParent().getFileSystem().getPath(linkTarget));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows without developer mode refuses these. Nothing in a Windows bundle needs
            // one, so a failure here is only fatal if it happens where links matter.
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                throw new IOException("could not create symbolic link " + destination, e);
            }
        }
    }

    private static void applyMode(Path file, int mode) throws IOException {
        if (mode == 0 || !Files.getFileStore(file).supportsFileAttributeView("posix")) {
            return;
        }
        Set<PosixFilePermission> permissions = new HashSet<>();
        if ((mode & 0400) != 0) {
            permissions.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) != 0) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) != 0) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0040) != 0) {
            permissions.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0010) != 0) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0004) != 0) {
            permissions.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0001) != 0) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        Files.setPosixFilePermissions(file, permissions);
    }

    /** Removes a directory and everything under it, following no links. */
    public static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
