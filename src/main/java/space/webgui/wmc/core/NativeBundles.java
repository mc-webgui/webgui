package space.webgui.wmc.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import space.webgui.wmc.api.Platform;

/**
 * The Chromium build this version of wmc runs, and the digest of every bundle of it.
 *
 * <p>The digests are written down here, in the source, rather than fetched alongside the
 * download. A checksum served by the same host as the file it describes rules out corruption
 * and nothing else; one that ships with the code says that the bytes are the ones this build
 * was tested against, which is the claim worth making.
 *
 * <p>Upgrading Chromium means changing {@link #JCEF_VERSION}, {@link #CEF_VERSION} and this
 * table together. They are deliberately in one place so that changing one and forgetting the
 * others is not possible.
 */
public final class NativeBundles {

    /** The upstream java-cef build, which is also the Maven version the bundles are under. */
    public static final String JCEF_VERSION =
            "jcef-ca49ada+cef-135.0.20+ge7de5c3+chromium-135.0.7049.85";

    /** The CEF version inside it, which names the directory the bundle installs into. */
    public static final String CEF_VERSION = "135.0.20";

    private static final Map<String, NativeArtifact> BY_PLATFORM = build();

    private NativeBundles() {
    }

    private static Map<String, NativeArtifact> build() {
        Map<String, NativeArtifact> bundles = new LinkedHashMap<>();
        add(bundles, "windows-amd64",
                "39d3edfd002c9b8e87b47aa435a2bc98424bccf7188a9ffc38b6a8104a40d494", 140_618_991L);
        add(bundles, "windows-arm64",
                "968377924fc730fd68728cc6c2dc32a9d8e4603806690f171280196e9afe4915", 141_892_063L);
        add(bundles, "linux-amd64",
                "25248d6063e470ddbb83bb59634d47d6fc8025c75abc921c2525ccb50abdeb2d", 135_042_800L);
        add(bundles, "linux-arm64",
                "584b30ce5bc000a63c4c9e7926e47786131a7af8a380891ed59ae5a01a7d05f6", 159_799_377L);
        add(bundles, "macos-amd64",
                "2fe3b772da86d4081a29f372e94d3bf20f03bd99bab1cfc57aeff889d12ab746", 128_557_210L);
        add(bundles, "macos-arm64",
                "327f27306ebd561ac353b157610f26d95d4a804fbf866f6ffe166686f2f89d1c", 120_348_948L);
        return Map.copyOf(bundles);
    }

    private static void add(Map<String, NativeArtifact> bundles, String platform, String sha256, long size) {
        bundles.put(platform, new NativeArtifact(CEF_VERSION, platform, sha256, size));
    }

    /** Every platform there is a bundle for. */
    public static Map<String, NativeArtifact> all() {
        return BY_PLATFORM;
    }

    /** The bundle for a platform, or empty if none is published for it. */
    public static Optional<NativeArtifact> forPlatform(String platform) {
        return Optional.ofNullable(BY_PLATFORM.get(platform));
    }

    /**
     * The bundle for the machine this is running on.
     *
     * @throws InstallException naming the platform, because "it did not work" on an
     *                          unsupported machine is a bug report nobody can act on
     */
    public static NativeArtifact forThisMachine() {
        String platform = Platform.current();
        return forPlatform(platform).orElseThrow(() -> new InstallException(
                "no browser runtime is published for " + platform + "; supported: " + BY_PLATFORM.keySet()));
    }
}
