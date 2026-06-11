package land.webgui.server;

//? if fabric {
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;*/
//? }

public final class WebviewPlaceholders {
    private WebviewPlaceholders() {}

    //? if fabric {
    public static String resolve(String template, ServerPlayerEntity player, Entity entity) {
        String entityId = entity.getUuid().toString();
        String entityType = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        return template
                .replace("{entity_id}",   entityId)
                .replace("{entity_uuid}", entityId)
                .replace("{entity_type}", entityType)
                .replace("{player_name}", player.getName().getString())
                .replace("{player_uuid}", player.getUuid().toString());
    }
    //? } else {
    /*public static String resolve(String template, ServerPlayer player, Entity entity) {
        String entityId = entity.getUUID().toString();
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return template
                .replace("{entity_id}",   entityId)
                .replace("{entity_uuid}", entityId)
                .replace("{entity_type}", entityType)
                .replace("{player_name}", player.getName().getString())
                .replace("{player_uuid}", player.getUUID().toString());
    }*/
    //? }
}
