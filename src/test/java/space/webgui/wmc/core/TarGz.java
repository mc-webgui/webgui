package space.webgui.wmc.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/** Builds small archives in memory, so the installer can be tested without downloading one. */
final class TarGz {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final TarArchiveOutputStream tar;

    TarGz() throws IOException {
        this.tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes));
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    }

    TarGz file(String name, String content, int mode) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(data.length);
        entry.setMode(mode);
        tar.putArchiveEntry(entry);
        tar.write(data);
        tar.closeArchiveEntry();
        return this;
    }

    TarGz file(String name, String content) throws IOException {
        return file(name, content, 0644);
    }

    TarGz directory(String name) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name.endsWith("/") ? name : name + "/");
        entry.setMode(0755);
        tar.putArchiveEntry(entry);
        tar.closeArchiveEntry();
        return this;
    }

    TarGz symlink(String name, String target) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK);
        entry.setLinkName(target);
        entry.setMode(0777);
        tar.putArchiveEntry(entry);
        tar.closeArchiveEntry();
        return this;
    }

    byte[] build() throws IOException {
        tar.finish();
        tar.close();
        return bytes.toByteArray();
    }
}
