package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchivesTest {

    @Test
    void extractsFilesAndDirectories(@TempDir Path target) throws IOException {
        byte[] archive = new TarGz().file("bin/run", "x").file("data/a.txt", "hello").build();

        int written = Archives.extractTarGz(new ByteArrayInputStream(archive), target);

        assertEquals(2, written);
        assertEquals("hello", Files.readString(target.resolve("data/a.txt")));
    }

    @Test
    void refusesAnEntryThatWouldEscapeTheTarget(@TempDir Path target) throws IOException {
        // The classic archive attack: without this check the entry lands outside the
        // directory being extracted into, anywhere the process can write.
        byte[] archive = new TarGz().file("../escaped.txt", "owned").build();

        assertThrows(InstallException.class,
                () -> Archives.extractTarGz(new ByteArrayInputStream(archive), target));
        assertFalse(Files.exists(target.getParent().resolve("escaped.txt")));
    }

    @Test
    void keepsTheExecuteBit(@TempDir Path target) throws IOException {
        assumeTrue(Files.getFileStore(target).supportsFileAttributeView("posix"),
                "posix permissions are not a thing on this filesystem");
        byte[] archive = new TarGz().file("helper", "#!/bin/sh\n", 0755).build();

        Archives.extractTarGz(new ByteArrayInputStream(archive), target);

        // A Chromium bundle ships helper executables. Lose this and the install looks
        // complete and refuses to start, which is a long afternoon.
        assertTrue(Files.getPosixFilePermissions(target.resolve("helper"))
                .contains(PosixFilePermission.OWNER_EXECUTE));
    }

    @Test
    void doesNotMakeEverythingExecutable(@TempDir Path target) throws IOException {
        assumeTrue(Files.getFileStore(target).supportsFileAttributeView("posix"));
        byte[] archive = new TarGz().file("data.pak", "x", 0644).build();

        Archives.extractTarGz(new ByteArrayInputStream(archive), target);

        assertFalse(Files.getPosixFilePermissions(target.resolve("data.pak"))
                .contains(PosixFilePermission.OWNER_EXECUTE));
    }

    @Test
    void deletesRecursivelyWithoutComplaining(@TempDir Path target) throws IOException {
        Files.createDirectories(target.resolve("a/b"));
        Files.writeString(target.resolve("a/b/c.txt"), "x");

        Archives.deleteRecursively(target.resolve("a"));
        Archives.deleteRecursively(target.resolve("never-existed"));

        assertFalse(Files.exists(target.resolve("a")));
    }

    @Test
    void unwrapsABundlePublishedInsideAJar(@TempDir Path target) throws IOException {
        // How the artifacts on Maven Central are shaped: a jar, because that is what a Maven
        // repository will host, with the real bundle as a single entry inside it.
        byte[] bundle = new TarGz().file("libcef.so", "pretend").build();
        Path jar = target.resolve("natives.jar");
        Files.write(jar, Jars.holding("jcef-natives-linux-amd64.tar.gz", bundle));
        Path into = Files.createDirectories(target.resolve("into"));

        assertEquals(1, Archives.extractBundle(jar, into));
        assertEquals("pretend", Files.readString(into.resolve("libcef.so")));
    }

    @Test
    void extractsAPlainBundleUnwrapped(@TempDir Path target) throws IOException {
        Path archive = target.resolve("bundle.tar.gz");
        Files.write(archive, new TarGz().file("libcef.so", "pretend").build());
        Path into = Files.createDirectories(target.resolve("into"));

        assertEquals(1, Archives.extractBundle(archive, into));
        assertTrue(Files.isRegularFile(into.resolve("libcef.so")));
    }

    @Test
    void refusesToGuessWhenAJarHoldsTwoBundles(@TempDir Path target) throws IOException {
        byte[] bundle = new TarGz().file("libcef.so", "pretend").build();
        Path jar = target.resolve("ambiguous.jar");
        Files.write(jar, Jars.holding("one.tar.gz", bundle, "two.tar.gz", bundle));

        assertThrows(InstallException.class,
                () -> Archives.extractBundle(jar, Files.createDirectories(target.resolve("into"))));
    }

    @Test
    void refusesSomethingThatIsNeither(@TempDir Path target) throws IOException {
        Path nonsense = target.resolve("nonsense.bin");
        Files.writeString(nonsense, "this is an html error page, not a bundle");

        // What a proxy or a login wall serves instead of the file. Extracting it would fail
        // somewhere deep in a decompressor with a message about neither.
        assertThrows(InstallException.class,
                () -> Archives.extractBundle(nonsense, Files.createDirectories(target.resolve("into"))));
    }

    @Test
    void readsANameTooLongForTheHeader() throws IOException {
        // Over a hundred characters, so the writer moves the name into a PAX record and the
        // header carries a stand-in. A reader that trusts the header alone extracts this
        // under the wrong name, which is how half a Chromium bundle ends up missing.
        String deep = "a-directory-with-a-long-name/".repeat(6) + "chrome-sandbox";
        assertTrue(deep.length() > 100);
        Path target = Files.createTempDirectory("long-name");
        byte[] archive = new TarGz().file(deep, "x", 0755).build();

        Archives.extractTarGz(new ByteArrayInputStream(archive), target);

        assertTrue(Files.isRegularFile(target.resolve(deep)), "the long name should survive");
        Archives.deleteRecursively(target);
    }

    @Test
    void keepsASymbolicLink(@TempDir Path target) throws IOException {
        // A macOS framework is mostly these. Extracted as empty regular files, the bundle
        // installs and Chromium will not start.
        assumeTrue(!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "Windows without developer mode refuses to create these");
        byte[] archive = new TarGz()
                .file("Versions/A/Helper", "helper", 0755)
                .symlink("Current", "Versions/A")
                .build();

        Archives.extractTarGz(new ByteArrayInputStream(archive), target);

        Path link = target.resolve("Current");
        assertTrue(Files.isSymbolicLink(link), "the entry should be extracted as a link");
        assertEquals("Versions/A",
                Files.readSymbolicLink(link).toString().replace(java.io.File.separatorChar, '/'));
    }

    @Test
    void refusesALinkThatWouldPointOutOfTheTarget(@TempDir Path target) throws IOException {
        byte[] archive = new TarGz().symlink("escape", "../../etc/passwd").build();

        assertThrows(InstallException.class,
                () -> Archives.extractTarGz(new ByteArrayInputStream(archive), target));
    }
}
