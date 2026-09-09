package land.webgui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A download's file name comes from the page — from a Content-Disposition header, in
 * practice — so it is attacker-controlled text on its way to a filesystem call. This
 * covers the two functions standing between the two.
 */
class WebviewDownloadNamesTest {

    @Test
    void keepsAnOrdinaryName() {
        assertEquals("invoice.pdf", WebviewDownloadNames.safeName("invoice.pdf"));
        assertEquals("my report (final).csv", WebviewDownloadNames.safeName("  my report (final).csv  "));
    }

    @Test
    void stripsAnyPathFromTheName() {
        assertEquals("evil.exe", WebviewDownloadNames.safeName("../../evil.exe"));
        assertEquals("evil.exe", WebviewDownloadNames.safeName("..\\..\\Windows\\System32\\evil.exe"));
        assertEquals("passwd", WebviewDownloadNames.safeName("/etc/passwd"));
        assertEquals("boot.ini", WebviewDownloadNames.safeName("C:\\boot.ini"));
    }

    @Test
    void replacesCharactersAFilesystemWouldRefuse() {
        assertEquals("a_b_c_d_e_f", WebviewDownloadNames.safeName("a:b*c?d\"e<f"));
        assertEquals("pipe_here", WebviewDownloadNames.safeName("pipe|here"));
        assertEquals("no_newline", WebviewDownloadNames.safeName("no\nnewline"));
    }

    @Test
    void neverProducesAHiddenOrEmptyName() {
        // A name of ".." or ".bashrc" is either a directory reference or a file the
        // player will never see; neither is what a download should become.
        assertEquals("download", WebviewDownloadNames.safeName(".."));
        assertEquals("download", WebviewDownloadNames.safeName(""));
        assertEquals("download", WebviewDownloadNames.safeName(null));
        assertEquals("download", WebviewDownloadNames.safeName("   "));
        assertEquals("bashrc", WebviewDownloadNames.safeName(".bashrc"));
    }

    @Test
    void sidestepsNamesWindowsReserves() {
        // Writing to these succeeds and goes nowhere, so the download would appear to
        // work and leave nothing behind.
        assertEquals("_CON", WebviewDownloadNames.safeName("CON"));
        assertEquals("_nul.txt", WebviewDownloadNames.safeName("nul.txt"));
        assertEquals("_COM1.log", WebviewDownloadNames.safeName("COM1.log"));
        assertEquals("console.txt", WebviewDownloadNames.safeName("console.txt"));
    }

    @Test
    void boundsTheLength() {
        String long_ = "x".repeat(500) + ".txt";

        String name = WebviewDownloadNames.safeName(long_);

        assertTrue(name.length() <= 120, "got " + name.length());
        assertFalse(name.isEmpty());
    }

    @Test
    void doesNotOverwriteAnExistingDownload(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("report.csv"), "first");

        Path second = WebviewDownloadNames.free(dir, "report.csv");

        assertEquals("report (1).csv", second.getFileName().toString());
    }

    @Test
    void keepsCountingPastTheFirstCollision(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "1");
        Files.writeString(dir.resolve("a (1).txt"), "2");

        assertEquals("a (2).txt", WebviewDownloadNames.free(dir, "a.txt").getFileName().toString());
    }

    @Test
    void countsExtensionlessNamesToo(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dump"), "1");

        assertEquals("dump (1)", WebviewDownloadNames.free(dir, "dump").getFileName().toString());
    }

    @Test
    void usesTheNameAsGivenWhenNothingIsThere(@TempDir Path dir) {
        assertEquals("fresh.txt", WebviewDownloadNames.free(dir, "fresh.txt").getFileName().toString());
    }

    // --- the other direction: what a file chooser is told to show ------------

    @Test
    void turnsAcceptEntriesIntoGlobs() {
        assertEquals(java.util.List.of("*.png", "*.jpg"),
                WebviewDownloadNames.globs(java.util.List.of(".png", ".jpg")));
        assertEquals(java.util.List.of("*.png"), WebviewDownloadNames.globs(java.util.List.of("png")));
        assertEquals(java.util.List.of("*.png"), WebviewDownloadNames.globs(java.util.List.of("*.png")));
    }

    @Test
    void dropsMimeTypesRatherThanInventingAPatternForThem() {
        // "image/png" has no glob; a made-up one would match nothing and hide every file
        // the player has, which reads as a broken upload rather than a wrong filter.
        assertEquals(java.util.List.of(), WebviewDownloadNames.globs(java.util.List.of("image/png")));
        assertEquals(java.util.List.of("*.png"),
                WebviewDownloadNames.globs(java.util.List.of("image/png", ".png")));
    }

    @Test
    void ignoresEmptyAndMissingAcceptEntries() {
        assertEquals(java.util.List.of("*.txt"),
                WebviewDownloadNames.globs(java.util.Arrays.asList("", "  ", null, ".txt")));
        assertTrue(WebviewDownloadNames.globs(null).isEmpty());
        assertTrue(WebviewDownloadNames.globs(java.util.List.of()).isEmpty());
    }
}
