package space.webgui.wmc.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * What a {@link SchemeHandler} answers with.
 *
 * <p>The body is a stream rather than a byte array so that a large file is not held in
 * memory twice. It is read off the game thread and closed by the runtime.
 */
public final class Resource {

    private final int status;
    private final String mimeType;
    private final Map<String, String> headers;
    private final InputStream body;

    private Resource(int status, String mimeType, Map<String, String> headers, InputStream body) {
        this.status = status;
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
        this.headers = Map.copyOf(headers);
        this.body = Objects.requireNonNull(body, "body");
    }

    public static Resource ok(String mimeType, InputStream body) {
        return new Resource(200, mimeType, Map.of(), body);
    }

    public static Resource ok(String mimeType, String body) {
        return ok(mimeType, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    public static Resource of(int status, String mimeType, Map<String, String> headers, InputStream body) {
        return new Resource(status, mimeType, headers, body);
    }

    /**
     * Nothing here.
     *
     * <p>A real 404 rather than an empty 200, so that a missing file looks like a missing
     * file in the page network panel instead of a blank one that loaded fine.
     */
    public static Resource notFound() {
        return new Resource(404, "text/plain", Map.of(), InputStream.nullInputStream());
    }

    public int status() {
        return status;
    }

    public String mimeType() {
        return mimeType;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public InputStream body() {
        return body;
    }
}
