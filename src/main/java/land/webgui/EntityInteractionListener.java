package land.webgui;

import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewEntityContext;
import land.webgui.server.WebviewPlaceholders;
//? if fabric {
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
//? } else {
/*import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;*/
//? }

public final class EntityInteractionListener {
    private EntityInteractionListener() {}

    //? if fabric {
    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient()) return ActionResult.PASS;

            var opt = EntityBindingStore.get(entity.getUuid());
            if (opt.isEmpty()) return ActionResult.PASS;

            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            EntityBinding b = opt.get();
            String url = WebviewPlaceholders.resolve(b.urlTemplate(), sp, entity);
            String entityJson = WebviewEntityContext.buildJson(entity);
            WebviewNetworking.openGuiForEntity(sp, url, entityJson);

            return b.cancelInteraction() ? ActionResult.SUCCESS : ActionResult.PASS;
        });
    }
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(EntityInteractionListener::onEntityInteract);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // The event fires on both sides, and on the client the player is a LocalPlayer.
        // Casting it crashed the game on the first right click of a bound entity. Only the
        // server can open a gui for a player anyway, which is what the Fabric side does.
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        var opt = EntityBindingStore.get(event.getTarget().getUUID());
        if (opt.isEmpty()) return;

        EntityBinding b = opt.get();
        String url = WebviewPlaceholders.resolve(b.urlTemplate(), sp, event.getTarget());
        String entityJson = WebviewEntityContext.buildJson(event.getTarget());
        WebviewNetworking.openGuiForEntity(sp, url, entityJson);

        if (b.cancelInteraction()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }*/
    //? }
}
