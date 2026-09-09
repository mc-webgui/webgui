package land.webgui;

//? if fabric {
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
//? } else {
/*import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;*/
//? }

/**
 * Client half of the join handshake: what the server said it is running.
 *
 * A protocol mismatch is silent by design everywhere else — payloads whose version does
 * not match are dropped so a newer server cannot drive an older client into undefined
 * behaviour — which leaves the player watching nothing happen. This is the one place that
 * says why, and names both versions so it is obvious which side to update.
 */
public final class WebGUIHandshake {

    private static String serverVersion = "";
    private static int serverProtocol = -1;

    private WebGUIHandshake() {}

    /** The server's WebGUI version, or empty if it never introduced itself. */
    public static String serverVersion() {
        return serverVersion;
    }

    public static int serverProtocol() {
        return serverProtocol;
    }

    public static void clear() {
        serverVersion = "";
        serverProtocol = -1;
    }

    private static String mismatchText(int protocol, String version) {
        return "[WebGUI] This server runs WebGUI " + version + " (protocol " + protocol
                + "), you have " + WebGUIVersion.current() + " (protocol "
                + WebviewNetworking.PROTOCOL_VERSION + "). Its pages will not open until the"
                + " versions match.";
    }

    //? if fabric {
    public static void onHello(int protocol, String version) {
        serverProtocol = protocol;
        serverVersion = version == null ? "" : version;
        if (protocol == WebviewNetworking.PROTOCOL_VERSION) {
            return;
        }
        WebGUIMod.LOGGER.warn("webgui: server runs {} (protocol {}), client is {} (protocol {})",
                serverVersion, protocol, WebGUIVersion.current(), WebviewNetworking.PROTOCOL_VERSION);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal(mismatchText(protocol, serverVersion)).formatted(Formatting.RED), false);
        }
    }
    //? } else {
    /*public static void onHello(int protocol, String version) {
        serverProtocol = protocol;
        serverVersion = version == null ? "" : version;
        if (protocol == WebviewNetworking.PROTOCOL_VERSION) {
            return;
        }
        WebGUIMod.LOGGER.warn("webgui: server runs {} (protocol {}), client is {} (protocol {})",
                serverVersion, protocol, WebGUIVersion.current(), WebviewNetworking.PROTOCOL_VERSION);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            // 26 dropped Player.displayClientMessage and gave LocalPlayer its own
            // sendSystemMessage instead.
            //? if >=26 {
            client.player.sendSystemMessage(
                    Component.literal(mismatchText(protocol, serverVersion)).withStyle(ChatFormatting.RED));
            //? } else {
            client.player.displayClientMessage(
                    Component.literal(mismatchText(protocol, serverVersion)).withStyle(ChatFormatting.RED), false);
            //? }
        }
    }*/
    //? }
}
