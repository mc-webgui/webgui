package land.webgui;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerConfig;
//? if fabric {
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
//? if >=1.21.5 {
import net.minecraft.command.DefaultPermissions;
//? }
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
//? } else {
/*import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;*/
//? }

import java.util.Collection;

public final class WebviewCommands {

    private WebviewCommands() {}

    //? if fabric {
    private static int bindEntities(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, boolean cancel)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgumentType.getEntities(ctx, "selector");
        String url = StringArgumentType.getString(ctx, "url");
        EntityBinding binding = new EntityBinding(url, cancel);
        for (Entity e : entities) {
            EntityBindingStore.bind(e.getUuid(), binding);
        }
        final int count = entities.size();
        ctx.getSource().sendFeedback(
                () -> Text.literal("WebGUI: bound " + count + " entity/entities → " + url),
                true);
        return count;
    }
    //? } else {
    /*private static int bindEntities(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean cancel)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "selector");
        String url = StringArgumentType.getString(ctx, "url");
        EntityBinding binding = new EntityBinding(url, cancel);
        for (Entity e : entities) {
            EntityBindingStore.bind(e.getUUID(), binding);
        }
        final int count = entities.size();
        ctx.getSource().sendSuccess(
                () -> Component.literal("WebGUI: bound " + count + " entity/entities -> " + url),
                true);
        return count;
    }*/
    //? }

    //? if fabric {
    //? if >=1.21.5 {
    private static boolean hasOp2(ServerCommandSource s) {
        return s.getPermissions().hasPermission(net.minecraft.command.DefaultPermissions.GAMEMASTERS);
    }
    //? } else {
    /*private static boolean hasOp2(ServerCommandSource s) {
        return s.hasPermissionLevel(2);
    }*/
    //? }
    //? } else {
    /*private static boolean hasOp2(CommandSourceStack s) {
        //? if >=1.21.5 {
        return net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions());
        //? } else {
        return s.hasPermission(2);
        //? }
    }*/
    //? }

    //? if fabric {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        CommandManager.literal("webgui")
                                .requires(WebviewCommands::hasOp2)
                                .then(CommandManager.literal("gui")
                                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");
                                                            String url = StringArgumentType.getString(ctx, "url");
                                                            for (ServerPlayerEntity p : players) {
                                                                WebviewNetworking.openGui(p, url);
                                                            }
                                                            ctx.getSource().sendFeedback(
                                                                    () -> Text.literal("Web GUI → " + players.size() + " player(s)"),
                                                                    true);
                                                            return players.size();
                                                        }))))
                                .then(CommandManager.literal("bind")
                                        .then(CommandManager.literal("entity")
                                                .then(CommandManager.argument("selector", EntityArgumentType.entities())
                                                        .then(CommandManager.argument("url", StringArgumentType.string())
                                                                .executes(ctx -> bindEntities(ctx, false))
                                                                .then(CommandManager.argument("cancel_interaction", BoolArgumentType.bool())
                                                                        .executes(ctx -> bindEntities(ctx,
                                                                                BoolArgumentType.getBool(ctx, "cancel_interaction"))))))))
                                .then(CommandManager.literal("unbind")
                                        .then(CommandManager.literal("entity")
                                                .then(CommandManager.argument("selector", EntityArgumentType.entities())
                                                        .executes(ctx -> {
                                                            Collection<? extends Entity> entities = EntityArgumentType.getEntities(ctx, "selector");
                                                            int count = 0;
                                                            for (Entity e : entities) {
                                                                if (EntityBindingStore.unbind(e.getUuid())) count++;
                                                            }
                                                            final int removed = count;
                                                            ctx.getSource().sendFeedback(
                                                                    () -> Text.literal("WebGUI: unbound " + removed + " entity/entities"),
                                                                    true);
                                                            return removed;
                                                        }))))
                                .then(CommandManager.literal("reload")
                                        .executes(ctx -> {
                                            String msg = WebviewServerConfig.reload();
                                            EntityBindingStore.load();
                                            ctx.getSource().sendFeedback(() -> Text.literal(msg), true);
                                            return 1;
                                        }))
                                .then(CommandManager.literal("hud")
                                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");
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
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebviewCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("webgui")
                        .requires(WebviewCommands::hasOp2)
                        .then(Commands.literal("gui")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    for (ServerPlayer p : players) {
                                                        WebviewNetworking.openGui(p, url);
                                                    }
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Web GUI -> " + players.size() + " player(s)"),
                                                            true);
                                                    return players.size();
                                                }))))
                        .then(Commands.literal("bind")
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("selector", EntityArgument.entities())
                                                .then(Commands.argument("url", StringArgumentType.string())
                                                        .executes(ctx -> bindEntities(ctx, false))
                                                        .then(Commands.argument("cancel_interaction", BoolArgumentType.bool())
                                                                .executes(ctx -> bindEntities(ctx,
                                                                        BoolArgumentType.getBool(ctx, "cancel_interaction"))))))))
                        .then(Commands.literal("unbind")
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("selector", EntityArgument.entities())
                                                .executes(ctx -> {
                                                    Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "selector");
                                                    int count = 0;
                                                    for (Entity e : entities) {
                                                        if (EntityBindingStore.unbind(e.getUUID())) count++;
                                                    }
                                                    final int removed = count;
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("WebGUI: unbound " + removed + " entity/entities"),
                                                            true);
                                                    return removed;
                                                }))))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    String msg = WebviewServerConfig.reload();
                                    EntityBindingStore.load();
                                    ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                                    return 1;
                                }))
                        .then(Commands.literal("hud")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    for (ServerPlayer p : players) {
                                                        WebviewNetworking.openHud(p, url);
                                                    }
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Web HUD -> " + players.size() + " player(s)"),
                                                            true);
                                                    return players.size();
                                                })))));
    }*/
    //? }
}
