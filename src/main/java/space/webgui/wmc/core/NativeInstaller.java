package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import space.webgui.wmc.api.RuntimeListener;

/**
 * Puts a Chromium bundle on disk, once, correctly, however many games start at the same time.
 *
 * <p>Four things have to hold, and each of them is a way this goes wrong in the wild:
 *
 * <ul>
 *   <li><strong>An install is complete or absent.</strong> Extraction happens beside the
 *       destination and is moved into place at the end, and a marker file is the last thing
 *       written. A directory without the marker is treated as absent and replaced: a
 *       half-extracted Chromium that looks installed fails later, somewhere unrelated.
 *   <li><strong>Two games starting together install once.</strong> A lock file around the
 *       whole operation, not just the download, because the second process would otherwise
 *       extract over the first.
 *   <li><strong>A wrong download never gets installed.</strong> The digest is checked before
 *       anything is extracted. A truncated archive is the common case on a bad connection,
 *       and it produces a browser that fails to start with no clue why.
 *   <li><strong>A part-finished download resumes.</strong> Three hundred megabytes over a
 *       poor connection should not start again from zero.
 * </ul>
 */
public final class NativeInstaller {

    private static final String MARKER = ".wmc-complete";
    private static final int BUFFER = 1024 * 1024;

    private final RuntimePaths paths;
    private final NativeSource source;

    public NativeInstaller(RuntimePaths paths, NativeSource source) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** Whether this bundle is already installed and complete. */
    public boolean isInstalled(NativeArtifact artifact) {
        return Files.isRegularFile(marker(artifact));
    }

    /**
     * Installs the bundle if it is not already there, and returns where it lives.
     *
     * <p>Blocking, and expected to be: it is called from a worker, never from the game thread.
     */
    // The lock is held for its side effect and never read, which javac reports under
    // -Xlint:try. Holding it is the point.
    @SuppressWarnings("try")
    public Path install(NativeArtifact artifact, RuntimeListener listener) {
        Path destination = paths.runtimeFor(artifact.cefVersion(), artifact.platform());
        if (isInstalled(artifact)) {
            return destination;
        }
        try {
            Files.createDirectories(destination.getParent());
            try (FileChannel channel = FileChannel.open(lockFile(artifact),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                // Checked again with the lock held: another process may have finished while
                // this one waited, and re-extracting over a good install is how a working
                // browser becomes a broken one.
                if (isInstalled(artifact)) {
                    return destination;
                }
                Path archive = fetch(artifact, listener);
                unpack(artifact, archive, destination);
                Files.deleteIfExists(archive);
                return destination;
            }
        } catch (IOException e) {
            throw new InstallException("could not install the browser runtime for " + artifact.platform(), e);
        }
    }

    private Path fetch(NativeArtifact artifact, RuntimeListener listener) throws IOException {
        Path partial = paths.runtimeRoot().resolve(artifact.fileName() + ".part");
        long have = Files.isRegularFile(partial) ? Files.size(partial) : 0;
        if (have > 0 && !source.supportsResume()) {
            Files.delete(partial);
            have = 0;
        }

        InputStream body;
        try {
            body = source.open(artifact, have);
        } catch (RangeIgnoredException wholeFile) {
            body = wholeFile.whole();
            have = 0;
        }

        try (InputStream in = body;
             OutputStream out = Files.newOutputStream(partial,
                     StandardOpenOption.CREATE, have > 0 ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[BUFFER];
            long received = have;
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                received += read;
                listener.onDownload(received, artifact.sizeBytes());
            }
        }

        String actual = Checksum.sha256(partial);
        if (!Checksum.matches(artifact.sha256(), actual)) {
            // Deleted rather than kept: a bad file that stays would be resumed from next time
            // and never become correct.
            Files.deleteIfExists(partial);
            throw new InstallException("the downloaded runtime did not match its checksum"
                    + " (expected " + artifact.sha256() + ", got " + actual + ")");
        }
        return partial;
    }

    private void unpack(NativeArtifact artifact, Path archive, Path destination) throws IOException {
        Path staging = destination.resolveSibling(destination.getFileName() + ".incoming");
        Archives.deleteRecursively(staging);
        Files.createDirectories(staging);
        Archives.extractBundle(archive, staging);

        Archives.deleteRecursively(destination);
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnavailable) {
            // Some filesystems refuse an atomic directory move. The marker still makes the
            // result honest: without it the directory reads as absent.
            Files.move(staging, destination);
        }
        Files.writeString(marker(artifact), artifact.sha256());
    }

    private Path marker(NativeArtifact artifact) {
        return paths.runtimeFor(artifact.cefVersion(), artifact.platform()).resolve(MARKER);
    }

    private Path lockFile(NativeArtifact artifact) {
        return paths.runtimeFor(artifact.cefVersion(), artifact.platform())
                .resolveSibling(artifact.platform() + ".lock");
    }
}
