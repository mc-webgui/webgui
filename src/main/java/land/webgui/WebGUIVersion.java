package land.webgui;

//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//? } else {
/*import net.neoforged.fml.ModList;*/
//? }

/**
 * The mod's own version string, as the loader reports it.
 *
 * Read once: both loaders build this from mod metadata that cannot change while the
 * game runs, and the handshake asks for it on every join.
 */
public final class WebGUIVersion {

    private static final String CURRENT = read();

    private WebGUIVersion() {}

    /** e.g. {@code 1.7.0+mc1.21.11}, or {@code "unknown"} if the loader has no metadata for us. */
    public static String current() {
        return CURRENT;
    }

    private static String read() {
        String v;
        //? if fabric {
        v = FabricLoader.getInstance()
                .getModContainer(WebGUIMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        //? } else {
        /*v = ModList.get()
                .getModContainerById(WebGUIMod.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("");*/
        //? }
        if (v == null || v.isBlank()) {
            return "unknown";
        }
        return v.length() > WebviewPayloads.MAX_VERSION_LENGTH
                ? v.substring(0, WebviewPayloads.MAX_VERSION_LENGTH)
                : v;
    }
}
