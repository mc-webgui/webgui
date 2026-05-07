package land.webgui.server;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WebviewUrlBuilder {
    private WebviewUrlBuilder() {}

    /**
     * Appends a query parameter; if the URL has a {@code #} fragment, the parameter is inserted before it.
     */
    public static String appendQueryParam(String url, String name, String value) {
        if (url == null) {
            url = "";
        }
        String encName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String encValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        String pair = encName + "=" + encValue;

        int hash = url.indexOf('#');
        String base;
        String fragment;
        if (hash >= 0) {
            base = url.substring(0, hash);
            fragment = url.substring(hash);
        } else {
            base = url;
            fragment = "";
        }
        if (base.contains("?")) {
            return base + "&" + pair + fragment;
        }
        return base + "?" + pair + fragment;
    }
}
