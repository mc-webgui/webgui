package land.webgui;

public final class WebGUIMainMenuUrl {
    private static String url = "";

    private WebGUIMainMenuUrl() {}

    public static void setUrl(String newUrl) {
        url = newUrl == null ? "" : newUrl;
    }

    public static String getUrl() {
        return url.isBlank() ? StartUrls.primary() : url;
    }
}
