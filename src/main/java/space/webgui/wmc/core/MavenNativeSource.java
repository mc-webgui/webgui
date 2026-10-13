package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Fetches bundles from a Maven repository, which is where they already are.
 *
 * <p>This is the answer to the hosting problem, and it is worth being explicit about why.
 * Chromium is well over a hundred megabytes per platform. Publishing that ourselves means
 * paying for storage and bandwidth forever, and means every player's first launch depends on
 * a server one person keeps alive — which is exactly the position the projects this one
 * replaces are in. The java-cef builds are already published to Maven Central, which is
 * immutable, mirrored, CDN-backed and not going away, so they are fetched from there and
 * nothing has to be hosted at all.
 *
 * <p>Each of those artifacts is a jar holding a single gzipped tar; {@code Archives} unwraps
 * it. The digests are pinned in {@link NativeBundles} rather than taken from the repository,
 * so a bundle is only installed if it is byte for byte the one this build was written against.
 */
public final class MavenNativeSource implements NativeSource {

    /** Maven Central, and the default for good reason: see the class documentation. */
    public static final String CENTRAL = "https://repo1.maven.org/maven2";

    private static final String GROUP = "me/friwi";

    private final HttpClient client;
    private final String repositoryUrl;
    private final String jcefVersion;

    /**
     * @param jcefVersion the full upstream build identifier, for example
     *                    {@code jcef-ca49ada+cef-135.0.20+ge7de5c3+chromium-135.0.7049.85}
     */
    public MavenNativeSource(String jcefVersion) {
        this(CENTRAL, jcefVersion, HttpFetch.client());
    }

    public MavenNativeSource(String repositoryUrl, String jcefVersion) {
        this(repositoryUrl, jcefVersion, HttpFetch.client());
    }

    MavenNativeSource(String repositoryUrl, String jcefVersion, HttpClient client) {
        this.repositoryUrl = Objects.requireNonNull(repositoryUrl, "repositoryUrl").replaceAll("/+$", "");
        this.jcefVersion = Objects.requireNonNull(jcefVersion, "jcefVersion");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    @Override
    public InputStream open(NativeArtifact artifact, long skipBytes) throws IOException {
        return HttpFetch.open(client, uriFor(artifact), skipBytes, artifact.fileName());
    }

    URI uriFor(NativeArtifact artifact) {
        String module = "jcef-natives-" + upstreamPlatform(artifact.platform());
        String version = encode(jcefVersion);
        return URI.create(repositoryUrl + "/" + GROUP + "/" + module + "/" + version
                + "/" + module + "-" + version + ".jar");
    }

    /**
     * Translates our platform names into the ones upstream publishes under.
     *
     * <p>Only macOS differs, and only in spelling. Keeping our own names rather than adopting
     * theirs means the installed directory layout does not change if the bundles ever come
     * from somewhere else.
     */
    static String upstreamPlatform(String platform) {
        return platform.toLowerCase(Locale.ROOT).startsWith("macos-")
                ? "macosx-" + platform.substring("macos-".length())
                : platform;
    }

    private static String encode(String version) {
        // The version contains '+' characters, which a Maven repository serves literally in
        // the path while a URL reader would otherwise read as spaces.
        return URLEncoder.encode(version, StandardCharsets.UTF_8);
    }
}
