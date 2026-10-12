package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import space.webgui.wmc.api.Platform;

class NativeBundlesTest {

    @Test
    void everyBundleIsPinnedToADigestAndASize() {
        assertTrue(NativeBundles.all().size() >= 6);
        for (Map.Entry<String, NativeArtifact> entry : NativeBundles.all().entrySet()) {
            NativeArtifact artifact = entry.getValue();
            assertEquals(entry.getKey(), artifact.platform());
            assertEquals(NativeBundles.CEF_VERSION, artifact.cefVersion());
            assertTrue(artifact.sha256().matches("[0-9a-f]{64}"),
                    entry.getKey() + " has no usable digest: " + artifact.sha256());
            assertTrue(artifact.sizeBytes() > 50_000_000L,
                    entry.getKey() + " is implausibly small for a Chromium bundle");
        }
    }

    @Test
    void theVersionsAgreeWithEachOther() {
        // Two places name the build, and an upgrade that changes one and not the other would
        // install a bundle into a directory named after a different Chromium.
        assertTrue(NativeBundles.JCEF_VERSION.contains("cef-" + NativeBundles.CEF_VERSION),
                NativeBundles.JCEF_VERSION + " does not contain cef-" + NativeBundles.CEF_VERSION);
    }

    @Test
    void thisMachineIsOneOfThem() {
        // Runs on Windows, Linux and macOS in CI, so this is three assertions in one: the
        // platform string the JVM produces has to be a key in the table on each of them.
        assertNotNull(NativeBundles.forThisMachine());
        assertEquals(Platform.current(), NativeBundles.forThisMachine().platform());
    }

    @Test
    void anUnsupportedMachineIsAnEmptyAnswerRatherThanAWrongBundle() {
        assertTrue(NativeBundles.forPlatform("solaris-sparc").isEmpty());
        assertTrue(NativeBundles.forPlatform("linux-i386").isEmpty(),
                "32-bit Linux is not published, and quietly handing back the 64-bit bundle"
                        + " would fail much later with nothing pointing here");
    }

    @Test
    void theTableCannotBeChangedByItsCallers() {
        assertThrows(UnsupportedOperationException.class,
                () -> NativeBundles.all().remove("linux-amd64"));
    }

    @Test
    void theClassesAndTheBundlesAreTheSameBuild() {
        // The jar of Java classes is the JNI declaration of the libraries in the bundle.
        // Upgrading one without the other links at build time and fails at the first native
        // call, with a message about a missing symbol rather than about a version.
        String fromBuild = System.getProperty("wmc.jcefVersion");
        assertNotNull(fromBuild, "the build should pass the version it resolves jcef-api at");
        assertEquals(fromBuild, NativeBundles.JCEF_VERSION);
    }
}
