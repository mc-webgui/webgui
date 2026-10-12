package space.webgui.wmc.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A request the page made against a scheme of yours.
 *
 * @param scheme  the scheme, without {@code ://}
 * @param host    the host part, which for {@code app://shop/index.html} is {@code shop}
 * @param path    the path, always starting with {@code /}
 * @param query   the raw query string without {@code ?}, or empty
 * @param method  the HTTP method, uppercase
 * @param headers request headers, keys lowercased
 */
public record SchemeRequest(
        String scheme,
        String host,
        String path,
        String query,
        String method,
        Map<String, List<String>> headers) {

    public SchemeRequest {
        headers = Map.copyOf(headers);
    }

    /** The first value of a header, if it was sent. */
    public Optional<String> header(String name) {
        List<String> values = headers.get(name.toLowerCase(java.util.Locale.ROOT));
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }
}
