package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where bundles come from.
 *
 * <p>An interface rather than a hard-coded URL so that the installer can be tested without a
 * network, which matters: the installer is the part most likely to be wrong, and a test that
 * needs three hundred megabytes to run is a test nobody runs.
 */
public interface NativeSource {

    /**
     * Opens the archive.
     *
     * @param skipBytes how many bytes the caller already has, for resuming a part-finished
     *                  download; sources that cannot resume must return the whole archive and
     *                  say so through {@link #supportsResume()}
     * @throws RangeIgnoredException if resuming was asked for and the whole archive came back
     *                               regardless, so the caller starts over instead of appending
     */
    InputStream open(NativeArtifact artifact, long skipBytes) throws IOException;

    /** Whether {@link #open} honours {@code skipBytes}. */
    default boolean supportsResume() {
        return false;
    }
}
