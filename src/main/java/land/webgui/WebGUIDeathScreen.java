package land.webgui;

/**
 * Client-side state for the custom death screen.
 *
 * The URL arrives on join, long before anyone dies, because the decision to
 * suppress the vanilla screen has to be made the instant Minecraft tries to
 * open it. Learning the URL at death time would be a race against the vanilla
 * death packet, and losing that race means the player sees the wrong screen.
 *
 * The info JSON arrives with the death itself and is held here rather than
 * pushed straight at the page: the page is only just being created at that
 * point, so an immediate emit would land before any listener exists. It is
 * replayed once the document has loaded instead.
 */
public final class WebGUIDeathScreen {

    private static String url = "";
    private static String pendingInfo = "";
    private static boolean active;
    private static boolean loadFailed;

    private WebGUIDeathScreen() {}

    public static void setUrl(String newUrl) {
        url = newUrl == null ? "" : newUrl.trim();
    }

    public static String url() {
        return url;
    }

    /** Whether the server asked for a custom death screen at all. */
    public static boolean configured() {
        return !url.isBlank();
    }

    public static void setInfo(String json) {
        pendingInfo = (json == null || json.isBlank()) ? "null" : json;
    }


    /** The death payload to hand the page, or null when there is nothing to replay. */
    public static String info() {
        return pendingInfo.isBlank() ? null : pendingInfo;
    }

    /**
     * True while our page is standing in for the vanilla death screen. Guards the
     * respawn channel so a page cannot respawn a player who is alive, and lets the
     * screen refuse to close on Escape the way the vanilla one does.
     */
    public static boolean active() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            loadFailed = false;
        }
    }

    /**
     * Set when the death page fails to load. Without an escape hatch a broken or
     * unreachable page would trap the player on a blank screen with no way to
     * respawn, which is worse than having no custom death screen at all.
     */
    public static boolean loadFailed() {
        return loadFailed;
    }

    public static void setLoadFailed(boolean value) {
        loadFailed = value;
    }

    /** Called on disconnect: none of this may carry across servers. */
    public static void clear() {
        url = "";
        pendingInfo = "";
        active = false;
        loadFailed = false;
    }
}
