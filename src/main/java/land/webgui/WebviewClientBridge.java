package land.webgui;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
//? if fabric {
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
//? } else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;*/
//? }

public final class WebviewClientBridge {
    private static final Gson GSON = new Gson();
    private static String lastSentJson;
    private static String pendingEntityContextJson;

    private WebviewClientBridge() {}

    /** Called from the client networking thread; the actual push happens on the next tick. */
    static void setEntityContext(String json) {
        pendingEntityContextJson = json;
        lastSentJson = null; // force re-push so the page gets updated immediately
    }

    static void clearCache() {
        lastSentJson = null;
        pendingEntityContextJson = null;
    }

    //? if fabric {
    public static void tick(MinecraftClient client) {
    //? } else {
    /*public static void tick(Minecraft client) {*/
    //? }
        tryPush(client, true, false);
    }

    //? if fabric {
    /** Always pushes, regardless of dedup — page must get data on first load. */
    public static void pushAfterDocumentLoad(MinecraftClient client) {
    //? } else {
    /*public static void pushAfterDocumentLoad(Minecraft client) {*/
    //? }
        tryPush(client, false, true);
    }

    //? if fabric {
    private static void tryPush(MinecraftClient client, boolean requireTexture, boolean ignoreDedup) {
        ClientPlayerEntity player = client.player;
    //? } else {
    /*private static void tryPush(Minecraft client, boolean requireTexture, boolean ignoreDedup) {
        LocalPlayer player = client.player;*/
    //? }
        if (!Rinku.isInitialized()) return;
        if (player == null) return;

        RinkuBrowser main = WebSession.browser();
        RinkuBrowser hud  = WebSession.hudBrowser();
        // isTextureReady on both loaders, not getRenderer().getTextureID() != 0 on one of
        // them. Rinku 3 keeps the browser's texture as a Blaze3D GpuTexture, which need
        // not have a raw GL id at all — on NeoForge 1.21.11 that id stayed 0, so this test
        // never passed and the page received exactly one payload, the one pushed when its
        // document loaded. Position, health, everything: frozen at the value it had on
        // open, with nothing in the log to say so.
        boolean hasMain = main != null && (!requireTexture || main.isTextureReady());
        boolean hasHud  = hud  != null && hud != main;
        if (!hasMain && !hasHud) return;

        JsonObject payload = buildPayload(client, player);
        String clientJson = GSON.toJson(payload);
        String entityLiteral = (pendingEntityContextJson != null && !pendingEntityContextJson.equals("null"))
                ? pendingEntityContextJson : "null";
        String dedupKey = clientJson + "|entity=" + entityLiteral;
        if (!ignoreDedup && dedupKey.equals(lastSentJson)) return;
        lastSentJson = dedupKey;

        String js = "(function(){"
                + "var c=" + clientJson + ";"
                + "var e=" + entityLiteral + ";"
                + "if(typeof window.webgui==='undefined')window.webgui={};"
                + "window.webgui.entity=e;"
                + "window.webgui.client=c;"
                + "try{window.dispatchEvent(new CustomEvent('webgui:entity',{detail:e}));}catch(ex){}"
                + "try{window.dispatchEvent(new CustomEvent('webgui:client',{detail:c}));}catch(ex){}"
                + "if(typeof window.webgui.onClientInfo==='function')try{window.webgui.onClientInfo(c);}catch(ex){console.error(ex);}"
                + "})();";

        if (hasMain) executeJs(main, js);
        if (hasHud)  executeJs(hud,  js);
    }

    private static void executeJs(RinkuBrowser browser, String js) {
        try {
            String url = browser.getURL();
            browser.executeJavaScript(js, url != null ? url : "", 0);
        } catch (Throwable t) {
            WebGUIMod.LOGGER.debug("webgui client bridge: {}", t.toString());
        }
    }

    /**
     * Strips the leading slash that {@link java.net.SocketAddress#toString()} prepends
     * (e.g. {@code /127.0.0.1:25565}). Done with plain string ops on purpose: feeding the
     * address to a regex (replaceFirst) crashes the game when it contains meta characters.
     */
    private static String stripLeadingSlash(String address) {
        return address.startsWith("/") ? address.substring(1) : address;
    }

    //? if fabric {
    private static JsonObject buildPayload(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject o = new JsonObject();

        o.addProperty("playerUuid",  player.getUuid().toString());
        o.addProperty("username",    player.getName().getString());
        o.addProperty("webviewMode", WebSession.mode().name());
        o.addProperty("dimension",   player.getEntityWorld().getRegistryKey().getValue().toString());

        o.addProperty("health",    player.getHealth());
        o.addProperty("maxHealth", player.getMaxHealth());
        o.addProperty("food",      player.getHungerManager().getFoodLevel());
        o.addProperty("xpLevel",   player.experienceLevel);
        var gameMode = client.interactionManager != null ? client.interactionManager.getCurrentGameMode() : null;
        if (gameMode != null) o.addProperty("gamemode", gameMode.asString());

        JsonObject pos = new JsonObject();
        pos.addProperty("x", player.getX());
        pos.addProperty("y", player.getY());
        pos.addProperty("z", player.getZ());
        o.add("pos", pos);

        JsonObject look = new JsonObject();
        look.addProperty("yaw",   player.getYaw());
        look.addProperty("pitch", player.getPitch());
        o.add("look", look);

        // The one camera value a page cannot work out for itself. Position and heading are
        // above, and the viewport is window.innerWidth/innerHeight, but the field of view
        // lives only in the player's own settings — and without it there is no way to turn
        // a point in the world into a point on the screen.
        //
        // This is the setting, not the momentary value: the renderer stretches it while
        // sprinting or under a speed effect, and reading that would mean hooking the
        // camera every frame. A marker drawn from this drifts a little during a sprint and
        // is exact when standing still.
        o.addProperty("fov", fov(client));
        o.add("lookingAt", buildLookingAt(client, player));

        JsonObject server = buildServerInfo(client);
        if (server != null) o.add("server", server);

        return o;
    }

    /** Vertical field of view in degrees, as the player set it. */
    private static int fov(MinecraftClient client) {
        return client.options.getFov().getValue();
    }

    /**
     * What the crosshair is on, if anything.
     *
     * The game already computes this every frame for its own use — the block outline, the
     * name above a mob, what a click would hit — so this is that same answer rather than a
     * second ray cast with its own idea of reach.
     *
     * Always present, with {@code type: "none"} when the player is looking at nothing. A
     * field that disappears makes every page write the same optional-chaining dance.
     */
    private static JsonObject buildLookingAt(MinecraftClient client, ClientPlayerEntity player) {
        JsonObject o = new JsonObject();
        net.minecraft.util.hit.HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            o.addProperty("type", "none");
            return o;
        }

        o.addProperty("distance", Math.sqrt(player.getEyePos().squaredDistanceTo(hit.getPos())));

        if (hit instanceof net.minecraft.util.hit.BlockHitResult block) {
            o.addProperty("type", "block");
            net.minecraft.util.math.BlockPos at = block.getBlockPos();
            JsonObject pos = new JsonObject();
            pos.addProperty("x", at.getX());
            pos.addProperty("y", at.getY());
            pos.addProperty("z", at.getZ());
            o.add("pos", pos);
            o.addProperty("face", block.getSide().asString());
            var world = player.getEntityWorld();
            o.addProperty("block", net.minecraft.registry.Registries.BLOCK
                    .getId(world.getBlockState(at).getBlock()).toString());
            return o;
        }

        if (hit instanceof net.minecraft.util.hit.EntityHitResult entityHit) {
            var entity = entityHit.getEntity();
            o.addProperty("type", "entity");
            o.addProperty("uuid", entity.getUuid().toString());
            o.addProperty("entityType", net.minecraft.entity.EntityType.getId(entity.getType()).toString());
            o.addProperty("name", entity.getName().getString());
            JsonObject pos = new JsonObject();
            pos.addProperty("x", entity.getX());
            pos.addProperty("y", entity.getY());
            pos.addProperty("z", entity.getZ());
            o.add("pos", pos);
            return o;
        }

        o.addProperty("type", "none");
        return o;
    }

    private static JsonObject buildServerInfo(MinecraftClient client) {
        var nh = client.getNetworkHandler();
        if (nh == null) return null;
        JsonObject s = new JsonObject();
        var entry = client.getCurrentServerEntry();
        if (entry != null) {
            s.addProperty("address", entry.address);
            if (entry.ping >= 0) s.addProperty("ping", entry.ping);
        } else {
            var conn = nh.getConnection();
            if (conn != null && conn.getAddress() != null) {
                s.addProperty("address", stripLeadingSlash(conn.getAddress().toString()));
            }
        }
        return s;
    }
    //? } else {
    /*private static JsonObject buildPayload(Minecraft client, LocalPlayer player) {
        JsonObject o = new JsonObject();

        o.addProperty("playerUuid",  player.getUUID().toString());
        o.addProperty("username",    player.getName().getString());
        o.addProperty("webviewMode", WebSession.mode().name());
        //? if >=1.21.5 {
        o.addProperty("dimension",   player.level().dimension().identifier().toString());
        //? } else {
        o.addProperty("dimension",   player.level().dimension().location().toString());
        //? }

        o.addProperty("health",    player.getHealth());
        o.addProperty("maxHealth", player.getMaxHealth());
        o.addProperty("food",      player.getFoodData().getFoodLevel());
        o.addProperty("xpLevel",   player.experienceLevel);
        var gameMode = client.gameMode != null ? client.gameMode.getPlayerMode() : null;
        if (gameMode != null) o.addProperty("gamemode", gameMode.getName());

        JsonObject pos = new JsonObject();
        pos.addProperty("x", player.getX());
        pos.addProperty("y", player.getY());
        pos.addProperty("z", player.getZ());
        o.add("pos", pos);

        JsonObject look = new JsonObject();
        look.addProperty("yaw",   player.getYRot());
        look.addProperty("pitch", player.getXRot());
        o.add("look", look);

        // The one camera value a page cannot work out for itself - see the note on the
        // Fabric side of this method.
        o.addProperty("fov", fov(client));
        o.add("lookingAt", buildLookingAt(client, player));

        JsonObject server = buildServerInfo(client);
        if (server != null) o.add("server", server);

        return o;
    }

    // Vertical field of view in degrees, as the player set it.
    private static int fov(Minecraft client) {
        return client.options.fov().get();
    }

    // What the crosshair is on, if anything. This is the game's own answer, the one it
    // uses for the block outline and the name above a mob, rather than a second ray cast
    // with its own idea of reach. Always present, with type "none" for nothing - a field
    // that disappears makes every page write the same optional-chaining dance.
    private static JsonObject buildLookingAt(Minecraft client, LocalPlayer player) {
        JsonObject o = new JsonObject();
        net.minecraft.world.phys.HitResult hit = client.hitResult;
        if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            o.addProperty("type", "none");
            return o;
        }

        o.addProperty("distance", Math.sqrt(player.getEyePosition().distanceToSqr(hit.getLocation())));

        if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
            o.addProperty("type", "block");
            net.minecraft.core.BlockPos at = block.getBlockPos();
            JsonObject pos = new JsonObject();
            pos.addProperty("x", at.getX());
            pos.addProperty("y", at.getY());
            pos.addProperty("z", at.getZ());
            o.add("pos", pos);
            o.addProperty("face", block.getDirection().getName());
            o.addProperty("block", net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(player.level().getBlockState(at).getBlock()).toString());
            return o;
        }

        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            var entity = entityHit.getEntity();
            o.addProperty("type", "entity");
            o.addProperty("uuid", entity.getStringUUID());
            o.addProperty("entityType", net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString());
            o.addProperty("name", entity.getName().getString());
            JsonObject pos = new JsonObject();
            pos.addProperty("x", entity.getX());
            pos.addProperty("y", entity.getY());
            pos.addProperty("z", entity.getZ());
            o.add("pos", pos);
            return o;
        }

        o.addProperty("type", "none");
        return o;
    }

    private static JsonObject buildServerInfo(Minecraft client) {
        var nh = client.getConnection();
        if (nh == null) return null;
        JsonObject s = new JsonObject();
        var entry = client.getCurrentServer();
        if (entry != null) {
            s.addProperty("address", entry.ip);
            if (entry.ping >= 0) s.addProperty("ping", entry.ping);
        } else {
            var conn = nh.getConnection();
            if (conn != null && conn.getRemoteAddress() != null) {
                s.addProperty("address", stripLeadingSlash(conn.getRemoteAddress().toString()));
            }
        }
        return s;
    }*/
    //? }
}
