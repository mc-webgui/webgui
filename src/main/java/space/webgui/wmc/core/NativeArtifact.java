package space.webgui.wmc.core;

import java.util.Objects;

/**
 * One downloadable Chromium bundle.
 *
 * @param cefVersion the CEF version it contains
 * @param platform   normalised as {@code os-arch}
 * @param sha256     the expected digest of the archive, lowercase hex
 * @param sizeBytes  the expected size, or {@code -1} when unknown
 */
public record NativeArtifact(String cefVersion, String platform, String sha256, long sizeBytes) {

    public NativeArtifact {
        Objects.requireNonNull(cefVersion, "cefVersion");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(sha256, "sha256");
        if (sha256.length() != 64) {
            throw new IllegalArgumentException("sha256 must be 64 hex characters, got " + sha256.length());
        }
    }

    /** The file name this bundle is published under. */
    public String fileName() {
        return "wmc-runtime-" + cefVersion + "-" + platform + ".tar.gz";
    }
}
