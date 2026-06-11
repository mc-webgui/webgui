package land.webgui.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
//? if fabric {
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
//? } else {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;*/
//? }

public final class WebviewEntityContext {
    private static final Gson GSON = new Gson();

    private WebviewEntityContext() {}

    public static String buildJson(Entity entity) {
        JsonObject o = new JsonObject();
        //? if fabric {
        o.addProperty("uuid", entity.getUuid().toString());
        o.addProperty("type", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        o.addProperty("name", entity.hasCustomName()
                ? entity.getCustomName().getString()
                : entity.getType().getName().getString());
        //? } else {
        /*o.addProperty("uuid", entity.getUUID().toString());
        o.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        o.addProperty("name", entity.hasCustomName()
                ? entity.getCustomName().getString()
                : entity.getType().getDescription().getString());*/
        //? }
        JsonObject pos = new JsonObject();
        pos.addProperty("x", entity.getX());
        pos.addProperty("y", entity.getY());
        pos.addProperty("z", entity.getZ());
        o.add("pos", pos);
        return GSON.toJson(o);
    }
}
