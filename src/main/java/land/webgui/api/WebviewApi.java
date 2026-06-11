package land.webgui.api;

import land.webgui.EntityBindingStore;
import land.webgui.WebviewNetworking;
import land.webgui.server.EntityBinding;
import land.webgui.server.WebviewServerEvents;
//? if fabric {
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.server.level.ServerPlayer;*/
//? }

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Call from the server thread. */
public final class WebviewApi {
    private WebviewApi() {}

    //? if fabric {
    /** If signed tokens are enabled in server.json, a token is appended to the URL automatically. */
    public static void openGui(ServerPlayerEntity player, String url) {
        WebviewNetworking.openGui(player, url);
    }

    /** If signed tokens are enabled in server.json, a token is appended to the URL automatically. */
    public static void openHud(ServerPlayerEntity player, String url) {
        WebviewNetworking.openHud(player, url);
    }

    /** Player opens this URL via the main menu keybind (default F6). */
    public static void sendMainMenuUrl(ServerPlayerEntity player, String url) {
        WebviewNetworking.sendMainMenuUrl(player, url);
    }

    /**
     * Sends a named event to the player's active WebGUI page(s).
     *
     * @param eventName   name of the event; the page receives it as {@code webgui:<eventName>}
     *                    via {@code window.addEventListener} or {@code window.webgui.on}
     * @param jsonPayload valid JSON value (object, array, string, number, boolean, or {@code "null"});
     *                    passed as {@code event.detail} in the browser
     */
    public static void emitToPage(ServerPlayerEntity player, String eventName, String jsonPayload) {
        WebviewNetworking.emitToPage(player, eventName, jsonPayload);
    }

    /**
     * Registers a server-side handler for events sent from the page via
     * {@code window.webgui.postToGame({ channel: "myEvent", ... })}.
     *
     * <p>Built-in channels ({@code "log"}, {@code "close"}) are handled client-side
     * and never reach this handler.
     *
     * @param channel the {@code channel} field value to match
     * @param handler receives (player, rawJsonPayload)
     */
    public static void onPageEvent(String channel, BiConsumer<ServerPlayerEntity, String> handler) {
        WebviewServerEvents.PAGE_EVENT.register(
                (player, ch, payload) -> { if (ch.equals(channel)) handler.accept(player, payload); });
    }
    //? } else {
    /*public static void openGui(ServerPlayer player, String url) {
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
        WebviewServerEvents.registerHandler(
                (player, ch, payload) -> { if (ch.equals(channel)) handler.accept(player, payload); });
    }*/
    //? }

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
