package land.webgui.server;

import land.webgui.WebGUIMod;
import land.webgui.WebGUIVersion;
import land.webgui.WebviewNetworking;
//? if fabric {
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
//? } else {
/*import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;*/
//? }

/**
 * Tells a joining player, in plain words, whether their client can see this server's pages.
 *
 * Without this the two loaders fail in two unhelpful ways: NeoForge rejects the connection
 * with "Incompatible client! Please use NeoForge &lt;x&gt;" — naming a mod that is not the
 * problem — while Fabric admits the player and then silently drops every WebGUI packet, so
 * nothing ever opens and nothing says why. Both now end with a message naming this mod and
 * the version the server runs.
 */
public final class WebviewClientCheck {

    private WebviewClientCheck() {}

    /** Whether the server has anything configured that a player without the mod would miss. */
    private static boolean serverUsesPages() {
        return !WebviewServerConfig.mainMenuUrl().isEmpty()
                || !WebviewServerConfig.deathScreenUrl().isEmpty()
                || WebviewServerConfig.autoHudOnJoin();
    }

    private static String outdatedText() {
        return "This server runs WebGUI " + WebGUIVersion.current()
                + " (protocol " + WebviewNetworking.PROTOCOL_VERSION + "). Your WebGUI is older and"
                + " cannot receive its pages — update it to " + WebGUIVersion.current() + ".";
    }

    private static String absentText() {
        return "This server runs WebGUI " + WebGUIVersion.current()
                + " and shows some of its interface through it. Your client does not have WebGUI,"
                + " so those pages will not appear.";
    }

    private static String kickText(WebviewNetworking.ClientKind kind) {
        return kind == WebviewNetworking.ClientKind.OUTDATED
                ? "Your WebGUI is out of date.\nThis server runs WebGUI " + WebGUIVersion.current()
                        + " (protocol " + WebviewNetworking.PROTOCOL_VERSION + ")."
                : "This server requires the WebGUI mod.\nIt runs WebGUI " + WebGUIVersion.current()
                        + " (protocol " + WebviewNetworking.PROTOCOL_VERSION + ").";
    }

    //? if fabric {
    public static boolean onJoin(ServerPlayerEntity player) {
        WebviewNetworking.ClientKind kind = WebviewNetworking.clientKind(player);
        if (kind == WebviewNetworking.ClientKind.CURRENT) {
            WebviewNetworking.sendHello(player);
            return true;
        }

        String name = player.getName().getString();
        if (WebviewServerConfig.requireClientMod()) {
            WebGUIMod.LOGGER.info("webgui: refused {} — client is {}", name, kind);
            player.networkHandler.disconnect(Text.literal(kickText(kind)));
            return false;
        }

        if (kind == WebviewNetworking.ClientKind.OUTDATED) {
            WebGUIMod.LOGGER.warn("webgui: {} has an outdated WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendMessage(Text.literal("[WebGUI] " + outdatedText()).formatted(Formatting.YELLOW), false);
        } else if (serverUsesPages()) {
            WebGUIMod.LOGGER.info("webgui: {} joined without WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendMessage(Text.literal("[WebGUI] " + absentText()).formatted(Formatting.GRAY), false);
        }
        return true;
    }
    //? } else {
    /*public static boolean onJoin(ServerPlayer player) {
        WebviewNetworking.ClientKind kind = WebviewNetworking.clientKind(player);
        if (kind == WebviewNetworking.ClientKind.CURRENT) {
            WebviewNetworking.sendHello(player);
            return true;
        }

        String name = player.getName().getString();
        if (WebviewServerConfig.requireClientMod()) {
            WebGUIMod.LOGGER.info("webgui: refused {} - client is {}", name, kind);
            player.connection.disconnect(Component.literal(kickText(kind)));
            return false;
        }

        if (kind == WebviewNetworking.ClientKind.OUTDATED) {
            WebGUIMod.LOGGER.warn("webgui: {} has an outdated WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendSystemMessage(Component.literal("[WebGUI] " + outdatedText()).withStyle(ChatFormatting.YELLOW));
        } else if (serverUsesPages()) {
            WebGUIMod.LOGGER.info("webgui: {} joined without WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendSystemMessage(Component.literal("[WebGUI] " + absentText()).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }*/
    //? }
}
