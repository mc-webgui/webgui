package land.webgui;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerConfig;
//? if fabric {
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
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

    private static int bindEntities(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean cancel)
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
    }

    private static boolean hasOp2(CommandSourceStack s) {
        //? if >=1.21.5 {
        return net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(s.permissions());
        //? } else {
        /*return s.hasPermission(2);
        *///? }
    }

    //? if fabric {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
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
                                                                    () -> Component.literal("Web GUI → " + players.size() + " player(s)"),
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
                                            // Every setting the client holds a copy of has to be
                                            // pushed again, or online players keep the old one.
                                            for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                WebviewNetworking.sendConfigSnapshot(p);
                                            }
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
                                                                    () -> Component.literal("Web HUD → " + players.size() + " player(s)"),
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
                                    // Every setting the client holds a copy of has to be pushed
                                    // again, or online players keep the old one.
                                    for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        WebviewNetworking.sendConfigSnapshot(p);
                                    }
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
