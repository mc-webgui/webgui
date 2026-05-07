package land.webgui;

public final class StartUrls {
    public static final String HTTPS_FALLBACK = "https://example.com";

    private StartUrls() {}

    public static String primary() {
        return HTTPS_FALLBACK;
    }
}
