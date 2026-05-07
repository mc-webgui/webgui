package land.webgui;

import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class WebviewCommands {

    public static final String PERM_GUI = "webgui.command.gui";
    public static final String PERM_HUD = "webgui.command.hud";

    private WebviewCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("webgui")
                                .requires(Permissions.require("webgui.command", 2))
                                .then(CommandManager.literal("gui")
                                        .requires(Permissions.require(PERM_GUI, 2))
                                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            var players = EntityArgumentType.getPlayers(ctx, "targets");
                                                            String url = StringArgumentType.getString(ctx, "url");
                                                            for (ServerPlayerEntity p : players) {
                                                                WebviewNetworking.openGui(p, url);
                                                            }
                                                            ctx.getSource().sendFeedback(
                                                                    () -> Text.literal("Web GUI → " + players.size() + " player(s)"),
                                                                    true);
                                                            return players.size();
                                                        }))))
                                .then(CommandManager.literal("hud")
                                        .requires(Permissions.require(PERM_HUD, 2))
                                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            var players = EntityArgumentType.getPlayers(ctx, "targets");
                                                            String url = StringArgumentType.getString(ctx, "url");
                                                            for (ServerPlayerEntity p : players) {
                                                                WebviewNetworking.openHud(p, url);
                                                            }
                                                            ctx.getSource().sendFeedback(
                                                                    () -> Text.literal("Web HUD → " + players.size() + " player(s)"),
                                                                    true);
                                                            return players.size();
                                                        }))))));
    }
}
