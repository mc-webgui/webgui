package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * The reader on its own, against archives a real tar writer produced.
 *
 * <p>Separate from {@link ArchivesTest} because what that one checks — the execute bit, a
 * symbolic link — cannot be checked on a filesystem that has neither, so it is skipped on
 * Windows, which is where this is most likely to be run. What a header says is readable
 * anywhere, and the header is the part written here; applying it to a file is not.
 */
class TarTest {

    private static Tar open(byte[] archive) throws IOException {
        return new Tar(new GZIPInputStream(new ByteArrayInputStream(archive)));
    }

    @Test
    void readsNamesSizesAndContentInOrder() throws IOException {
        byte[] archive = new TarGz()
                .file("first.txt", "one")
                .file("nested/second.txt", "twotwo")
                .build();

        try (Tar tar = open(archive)) {
            Tar.Entry first = tar.next();
            assertEquals("first.txt", first.name());
            assertEquals(3, first.size());
            assertEquals("one", new String(tar.readAllBytes(), StandardCharsets.UTF_8));

            Tar.Entry second = tar.next();
            assertEquals("nested/second.txt", second.name());
            assertEquals("twotwo", new String(tar.readAllBytes(), StandardCharsets.UTF_8));

            assertNull(tar.next(), "the archive should end after its entries");
        }
    }

    @Test
    void stopsReadingAtTheEndOfAnEntry() throws IOException {
        // The whole point of bounding the stream: handed to Files.copy, an entry must not
        // run on into the padding and the next header.
        byte[] archive = new TarGz().file("a", "aaa").file("b", "bbb").build();

        try (Tar tar = open(archive)) {
            tar.next();
            assertEquals("aaa", new String(tar.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(-1, tar.read(), "the entry should be exhausted");
            assertEquals("b", tar.next().name());
        }
    }

    @Test
    void skipsAnEntryNobodyRead() throws IOException {
        // A directory or a link is never read from, and the bytes of a file may be skipped
        // too; the next header still has to be found.
        byte[] archive = new TarGz().file("big", "x".repeat(1500)).file("after", "y").build();

        try (Tar tar = open(archive)) {
            assertEquals("big", tar.next().name());
            assertEquals("after", tar.next().name(), "an unread entry should be stepped over");
        }
    }

    @Test
    void keepsTheMode() throws IOException {
        byte[] archive = new TarGz()
                .file("chrome-sandbox", "#!", 0755)
                .file("icudtl.dat", "data", 0644)
                .build();

        try (Tar tar = open(archive)) {
            assertEquals(0755, tar.next().mode(), "an executable's mode should survive");
            assertEquals(0644, tar.next().mode());
        }
    }

    @Test
    void readsASymbolicLinkAndWhereItPoints() throws IOException {
        byte[] archive = new TarGz().symlink("Current", "Versions/A").build();

        try (Tar tar = open(archive)) {
            Tar.Entry link = tar.next();
            assertTrue(link.isSymbolicLink(), "the type flag should say it is a link");
            assertFalse(link.isFile());
            assertEquals("Versions/A", link.linkName());
        }
    }

    @Test
    void readsADirectory() throws IOException {
        byte[] archive = new TarGz().directory("locales").file("locales/en-US.pak", "p").build();

        try (Tar tar = open(archive)) {
            Tar.Entry directory = tar.next();
            assertTrue(directory.isDirectory());
            assertFalse(directory.isFile());
            assertEquals("locales/en-US.pak", tar.next().name());
        }
    }

    @Test
    void readsANameThatDoesNotFitTheHeader() throws IOException {
        // Past a hundred characters the writer moves the name out of the header into a PAX
        // record, and the header carries a stand-in name instead. Reading the header alone
        // gives the wrong path for a good part of a Chromium bundle.
        String deep = "a-directory-with-a-long-name/".repeat(6) + "chrome-sandbox";
        assertTrue(deep.length() > 100);
        byte[] archive = new TarGz().file(deep, "x", 0755).build();

        try (Tar tar = open(archive)) {
            Tar.Entry entry = tar.next();
            assertEquals(deep, entry.name());
            assertEquals(0755, entry.mode(), "the mode belongs to the entry, not to the record");
        }
    }

    @Test
    void readsALinkTargetThatDoesNotFitTheHeader() throws IOException {
        String deep = "Versions/" + "a-long-framework-directory/".repeat(5) + "Helper";
        assertTrue(deep.length() > 100);
        byte[] archive = new TarGz().symlink("Current", deep).build();

        try (Tar tar = open(archive)) {
            Tar.Entry link = tar.next();
            assertTrue(link.isSymbolicLink());
            assertEquals(deep, link.linkName());
        }
    }

    @Test
    void endsOnAnArchiveWithNothingInIt() throws IOException {
        try (Tar tar = open(new TarGz().build())) {
            assertNull(tar.next());
        }
    }
}
