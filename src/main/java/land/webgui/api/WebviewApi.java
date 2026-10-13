package land.webgui.api;

import land.webgui.EntityBindingStore;
import land.webgui.WebviewNetworking;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Call from the server thread. */
public final class WebviewApi {
    private WebviewApi() {}

    public static void openGui(ServerPlayer player, String url) {
        WebviewNetworking.openGui(player, url);
    }
    public static void openHud(ServerPlayer player, String url) {
        WebviewNetworking.openHud(player, url);
    }
    public static void sendMainMenuUrl(ServerPlayer player, String url) {
        WebviewNetworking.sendMainMenuUrl(player, url);
    }
    public static void emitToPage(ServerPlayer player, String eventName, String jsonPayload) {
        WebviewNetworking.emitToPage(player, eventName, jsonPayload);
    }
    public static void onPageEvent(String channel, BiConsumer<ServerPlayer, String> handler) {
        // The only thing that still differs here: Fabric has an event bus of its own, and
        // NeoForge is handed a plain list. Everything around it is now the same on both.
        //? if fabric {
        WebviewServerEvents.PAGE_EVENT.register(
                (player, ch, payload) -> { if (ch.equals(channel)) handler.accept(player, payload); });
        //? } else {
        /*WebviewServerEvents.registerHandler(
                (player, ch, payload) -> { if (ch.equals(channel)) handler.accept(player, payload); });*/
        //? }
    }

    /**
     * Binds an entity to a WebGUI URL. When a player right-clicks the entity, the GUI opens.
     *
     * @param entityUuid        UUID of the entity to bind
     * @param urlTemplate       URL to open; supports {entity_id}, {entity_uuid}, {entity_type},
     *                          {player_name}, {player_uuid} placeholders
     * @param cancelInteraction if true, the default entity interaction (e.g. villager trade) is suppressed
     */
    public static void bindEntity(UUID entityUuid, String urlTemplate, boolean cancelInteraction) {
        EntityBindingStore.bind(entityUuid, new EntityBinding(urlTemplate, cancelInteraction));
    }

    /** Removes the WebGUI binding for the given entity UUID. */
    public static void unbindEntity(UUID entityUuid) {
        EntityBindingStore.unbind(entityUuid);
    }

    /** Returns the entity binding for the given UUID, if one exists. */
    public static Optional<EntityBinding> getEntityBinding(UUID entityUuid) {
        return EntityBindingStore.get(entityUuid);
    }

    /** Maximum URL length accepted by the mod (16 384 characters). */
    public static int maxUrlLength() {
        return WebviewNetworking.MAX_URL_LENGTH;
    }

    /** S2C protocol version this build of the mod uses. */
    public static int protocolVersion() {
        return WebviewNetworking.PROTOCOL_VERSION;
    }
}
