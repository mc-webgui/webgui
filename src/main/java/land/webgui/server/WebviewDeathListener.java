package land.webgui.server;

import com.google.gson.JsonObject;
import land.webgui.WebviewNetworking;
//? if fabric {
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;*/
//? }

/**
 * Reports a player's death to their custom death page.
 *
 * Runs only when the server configured {@code deathScreenUrl}; with no page to
 * show there is nothing to report and the vanilla screen handles it.
 */
public final class WebviewDeathListener {

    private WebviewDeathListener() {}

    //? if fabric {
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                onPlayerDeath(player, source);
            }
        });
    }

    private static void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        String url = WebviewServerConfig.deathScreenUrl();
        if (url.isEmpty()) {
            return;
        }
        WebviewNetworking.sendDeathScreen(player, url, describe(player, source).toString());
    }

    private static JsonObject describe(ServerPlayerEntity player, DamageSource source) {
        JsonObject o = new JsonObject();
        o.addProperty("cause", source == null ? "generic" : source.getName());
        o.addProperty("deathMessage", source == null
                ? ""
                : source.getDeathMessage(player).getString());
        boolean hardcore = player.getEntityWorld().getLevelProperties().isHardcore();
        o.addProperty("hardcore", hardcore);
        // Hardcore has no respawn button, so the page must not offer one either.
        o.addProperty("canRespawn", !hardcore);
        o.add("killer", killer(source));
        return o;
    }

    private static JsonObject killer(DamageSource source) {
        JsonObject k = new JsonObject();
        Entity attacker = source == null ? null : source.getAttacker();
        if (attacker == null) {
            // Fell, drowned, burned — no entity to name.
            k.addProperty("type", "environment");
            k.add("name", com.google.gson.JsonNull.INSTANCE);
            k.add("uuid", com.google.gson.JsonNull.INSTANCE);
            return k;
        }
        boolean isPlayer = attacker instanceof ServerPlayerEntity;
        k.addProperty("type", isPlayer ? "player" : "mob");
        k.addProperty("name", attacker.getName().getString());
        k.addProperty("uuid", attacker.getUuid().toString());
        if (!isPlayer) {
            // The registry id ("minecraft:zombie"), not EntityType.toString(),
            // which yields the translation key and is awkward to match on.
            k.addProperty("entityType", EntityType.getId(attacker.getType()).toString());
        }
        return k;
    }
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebviewDeathListener::onLivingDeath);
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        String url = WebviewServerConfig.deathScreenUrl();
        if (url.isEmpty()) {
            return;
        }
        WebviewNetworking.sendDeathScreen(player, url, describe(player, event.getSource()).toString());
    }

    private static JsonObject describe(ServerPlayer player, DamageSource source) {
        JsonObject o = new JsonObject();
        o.addProperty("cause", source == null ? "generic" : source.getMsgId());
        o.addProperty("deathMessage", source == null
                ? ""
                : source.getLocalizedDeathMessage(player).getString());
        boolean hardcore = player.level().getLevelData().isHardcore();
        o.addProperty("hardcore", hardcore);
        // Hardcore has no respawn button, so the page must not offer one either.
        o.addProperty("canRespawn", !hardcore);
        o.add("killer", killer(source));
        return o;
    }

    private static JsonObject killer(DamageSource source) {
        JsonObject k = new JsonObject();
        Entity attacker = source == null ? null : source.getEntity();
        if (attacker == null) {
            // Fell, drowned, burned — no entity to name.
            k.addProperty("type", "environment");
            k.add("name", com.google.gson.JsonNull.INSTANCE);
            k.add("uuid", com.google.gson.JsonNull.INSTANCE);
            return k;
        }
        boolean isPlayer = attacker instanceof ServerPlayer;
        k.addProperty("type", isPlayer ? "player" : "mob");
        k.addProperty("name", attacker.getName().getString());
        k.addProperty("uuid", attacker.getStringUUID());
        if (!isPlayer) {
            // The registry id ("minecraft:zombie"), not EntityType.toString(),
            // which yields the translation key and is awkward to match on.
            k.addProperty("entityType", EntityType.getKey(attacker.getType()).toString());
        }
        return k;
    }*/
    //? }
}
