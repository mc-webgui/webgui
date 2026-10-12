package space.webgui.wmc.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Wraps bundles in a jar the way the published artifacts do. */
final class Jars {

    private Jars() {
    }

    /** A jar holding the given entries, each a name and its bytes. */
    static byte[] holding(String name, byte[] content) throws IOException {
        return holding(name, content, null, null);
    }

    static byte[] holding(String first, byte[] firstContent, String second, byte[] secondContent)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(first));
            zip.write(firstContent);
            zip.closeEntry();
            if (second != null) {
                zip.putNextEntry(new ZipEntry(second));
                zip.write(secondContent);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
