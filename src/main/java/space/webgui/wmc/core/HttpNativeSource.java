package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;

/**
 * Fetches bundles from a plain web server, resuming where it left off.
 *
 * <p>Resuming is not a nicety at this size. Three hundred megabytes over a domestic connection
 * is minutes, and a connection dropping at ninety percent should not mean starting from
 * nothing.
 *
 * <p>The layout is {@code <base>/<cef version>/<file name>}.
 */
public final class HttpNativeSource implements NativeSource {

    private final HttpClient client;
    private final String baseUrl;

    public HttpNativeSource(String baseUrl) {
        this(baseUrl, HttpFetch.client());
    }

    HttpNativeSource(String baseUrl, HttpClient client) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/+$", "");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    @Override
    public InputStream open(NativeArtifact artifact, long skipBytes) throws IOException {
        URI uri = URI.create(baseUrl + "/" + artifact.cefVersion() + "/" + artifact.fileName());
        return HttpFetch.open(client, uri, skipBytes, artifact.fileName());
    }
}
