package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Layout discovery against the three shapes a bundle actually comes in.
 *
 * <p>Built as directories of empty files rather than by unpacking a real bundle: what is being
 * tested is whether the landmarks are recognised, and an empty file is as recognisable as a
 * two hundred megabyte one. It also means the macOS shape is covered from any machine.
 */
class BundleLayoutTest {

    private static Path tree(Path root, String... entries) throws IOException {
        for (String entry : entries) {
            Path path = root.resolve(entry);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "");
        }
        return root;
    }

    private static Path windows(Path root) throws IOException {
        return tree(root, "libcef.dll", "jcef.dll", "chrome_elf.dll", "jcef_helper.exe",
                "icudtl.dat", "resources.pak", "locales/en-US.pak");
    }

    private static Path linux(Path root) throws IOException {
        return tree(root, "libcef.so", "libjcef.so", "jcef_helper",
                "icudtl.dat", "resources.pak", "locales/en-US.pak");
    }

    private static Path macos(Path root) throws IOException {
        return tree(root,
                "Chromium Embedded Framework.framework/Chromium Embedded Framework",
                "Chromium Embedded Framework.framework/Resources/icudtl.dat",
                "Chromium Embedded Framework.framework/Resources/en.lproj/locale.pak",
                "libjcef.dylib",
                "jcef Helper.app/Contents/MacOS/jcef Helper",
                "jcef Helper (GPU).app/Contents/MacOS/jcef Helper (GPU)");
    }

    @Test
    void findsEverythingInAWindowsBundle(@TempDir Path root) throws IOException {
        BundleLayout layout = BundleLayout.discover(windows(root));

        assertEquals(root.resolve("libcef.dll"), layout.library());
        assertEquals(root.resolve("jcef_helper.exe"), layout.helper());
        assertEquals(root, layout.resourcesDirectory());
        assertEquals(root.resolve("locales"), layout.localesDirectory().orElseThrow());
        assertEquals(root.resolve("jcef.dll"), layout.nativeLibrary("jcef").orElseThrow());
    }

    @Test
    void findsEverythingInALinuxBundle(@TempDir Path root) throws IOException {
        BundleLayout layout = BundleLayout.discover(linux(root));

        assertEquals(root.resolve("libcef.so"), layout.library());
        assertEquals(root.resolve("jcef_helper"), layout.helper());
        assertEquals(root, layout.resourcesDirectory());
        assertTrue(layout.localesDirectory().isPresent());
    }

    @Test
    void findsEverythingInAMacosBundle(@TempDir Path root) throws IOException {
        BundleLayout layout = BundleLayout.discover(macos(root));

        assertEquals(root.resolve("Chromium Embedded Framework.framework")
                .resolve("Chromium Embedded Framework"), layout.library());
        // Resources live inside the framework there, not beside the library, which is exactly
        // the difference a hard-coded per-platform table gets wrong.
        assertEquals(root.resolve("Chromium Embedded Framework.framework").resolve("Resources"),
                layout.resourcesDirectory());
        assertTrue(layout.localesDirectory().isEmpty(),
                "macOS keeps locales inside the framework resources and CEF finds them itself");
    }

    @Test
    void picksTheMainHelperRatherThanOneOfItsVariants(@TempDir Path root) throws IOException {
        // A macOS bundle ships four helpers. Naming the GPU one as the subprocess path starts
        // every renderer as a GPU process, which fails in a way that points nowhere useful.
        BundleLayout layout = BundleLayout.discover(macos(root));

        assertEquals("jcef Helper", layout.helper().getFileName().toString());
    }

    @Test
    void saysWhatIsMissingRatherThanFailingLater(@TempDir Path root) throws IOException {
        tree(root, "libcef.dll", "icudtl.dat");

        InstallException e = assertThrows(InstallException.class, () -> BundleLayout.discover(root));
        assertTrue(e.getMessage().contains("helper"), e.getMessage());
        assertTrue(e.getMessage().contains(root.toString()), e.getMessage());
    }

    @Test
    void refusesADirectoryThatIsNotThere(@TempDir Path root) {
        assertThrows(InstallException.class, () -> BundleLayout.discover(root.resolve("nothing")));
    }

    @Test
    void findsTheFrameworkOnMacosAndNowhereElse(@TempDir Path root) throws IOException {
        assertEquals("Chromium Embedded Framework.framework",
                BundleLayout.discover(macos(root)).frameworkDirectory().orElseThrow()
                        .getFileName().toString());
        assertTrue(BundleLayout.discover(windows(root.resolve("win"))).frameworkDirectory().isEmpty());
    }
}
