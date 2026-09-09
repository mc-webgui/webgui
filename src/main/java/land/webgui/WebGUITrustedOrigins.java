package land.webgui;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Client-side registry of origins ({@code scheme://host[:port]}) whose pages may run commands as
 * the player. Populated from the server on join and cleared on disconnect, so trust never carries
 * across servers. A page whose origin is not listed cannot trigger command execution.
 */
public final class WebGUITrustedOrigins {
    private static volatile Set<String> origins = Collections.emptySet();

    private WebGUITrustedOrigins() {}

    /** Replaces the trusted set from a newline-joined list sent by the server. */
    public static void set(String joined) {
        Set<String> next = new HashSet<>();
        if (joined != null) {
            for (String line : joined.split("\n")) {
                String o = normalize(line);
                if (o != null) next.add(o);
            }
        }
        origins = next;
    }

    /**
     * Adds one more origin to the set the server sent.
     *
     * Used for the pages the server ships itself, which are served from loopback and so
     * have an origin the server cannot know in advance. Nobody but that server can put a
     * file there, so trusting it is no wider than trusting the list it just sent.
     */
    public static void allowAlso(String url) {
        String o = normalize(url);
        if (o == null) {
            return;
        }
        Set<String> next = new HashSet<>(origins);
        next.add(o);
        origins = next;
    }

    public static void clear() {
        origins = Collections.emptySet();
    }

    /** True if the given page URL's origin is trusted for command execution. */
    public static boolean isTrusted(String url) {
        String o = normalize(url);
        return o != null && origins.contains(o);
    }

    /** Reduces a URL to {@code scheme://host[:port]} (default ports dropped), lowercased; null if unusable. */
    static String normalize(String url) {
        if (url == null) return null;
        String s = url.trim();
        if (s.isEmpty()) return null;
        try {
            URI u = URI.create(s);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            String origin = scheme + "://" + host;
            int port = u.getPort();
            if (port != -1 && !isDefaultPort(scheme, port)) {
                origin = origin + ":" + port;
            }
            return origin;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
    }
}
