package land.webgui.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

public final class WebviewPlaceholders {
    private WebviewPlaceholders() {}

    public static String resolve(String template, ServerPlayer player, Entity entity) {
        String entityId = entity.getUUID().toString();
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return template
                .replace("{entity_id}",   entityId)
                .replace("{entity_uuid}", entityId)
                .replace("{entity_type}", entityType)
                .replace("{player_name}", player.getName().getString())
                .replace("{player_uuid}", player.getUUID().toString());
    }
}
