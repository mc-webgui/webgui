package land.webgui;

//? if >=1.20.5 {
//? if fabric {
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
//? } else {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;*/
//? }
//? }
//? if fabric {
import net.minecraft.util.Identifier;
//? } else {
/*//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? } else {
import net.minecraft.resources.ResourceLocation;
//? }*/
//? }

public final class WebviewPayloads {
    private WebviewPayloads() {}

    public static final int MAX_EVENT_NAME_LENGTH = 256;
    public static final int MAX_EVENT_DATA_LENGTH = 32_768;
    public static final int MAX_VERSION_LENGTH = 64;

    // Channel identifiers — used in both legacy (1.20.1) and modern networking
    //? if fabric {
    public static final Identifier OPEN_WEB_CHANNEL       = Identifier.of(WebGUIMod.MOD_ID, "open_web");
    public static final Identifier MAIN_MENU_CHANNEL      = Identifier.of(WebGUIMod.MOD_ID, "set_main_menu");
    public static final Identifier EMIT_TO_PAGE_CHANNEL   = Identifier.of(WebGUIMod.MOD_ID, "emit_to_page");
    public static final Identifier PAGE_EVENT_CHANNEL     = Identifier.of(WebGUIMod.MOD_ID, "page_event");
    public static final Identifier ENTITY_CONTEXT_CHANNEL = Identifier.of(WebGUIMod.MOD_ID, "entity_context");
    public static final Identifier TRUSTED_ORIGINS_CHANNEL = Identifier.of(WebGUIMod.MOD_ID, "trusted_origins");
    public static final Identifier DEATH_SCREEN_CHANNEL   = Identifier.of(WebGUIMod.MOD_ID, "death_screen");
    public static final Identifier HELLO_CHANNEL          = Identifier.of(WebGUIMod.MOD_ID, "hello");
    //? } else {
    /*//? if >=1.21.5 {
    public static final Identifier OPEN_WEB_CHANNEL       = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "open_web");
    public static final Identifier MAIN_MENU_CHANNEL      = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "set_main_menu");
    public static final Identifier EMIT_TO_PAGE_CHANNEL   = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "emit_to_page");
    public static final Identifier PAGE_EVENT_CHANNEL     = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "page_event");
    public static final Identifier ENTITY_CONTEXT_CHANNEL = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "entity_context");
    public static final Identifier TRUSTED_ORIGINS_CHANNEL = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "trusted_origins");
    public static final Identifier DEATH_SCREEN_CHANNEL   = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "death_screen");
    public static final Identifier HELLO_CHANNEL          = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "hello");
    //? } else {
    public static final ResourceLocation OPEN_WEB_CHANNEL       = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "open_web");
    public static final ResourceLocation MAIN_MENU_CHANNEL      = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "set_main_menu");
    public static final ResourceLocation EMIT_TO_PAGE_CHANNEL   = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "emit_to_page");
    public static final ResourceLocation PAGE_EVENT_CHANNEL     = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "page_event");
    public static final ResourceLocation ENTITY_CONTEXT_CHANNEL = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "entity_context");
    public static final ResourceLocation TRUSTED_ORIGINS_CHANNEL = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "trusted_origins");
    public static final ResourceLocation DEATH_SCREEN_CHANNEL   = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "death_screen");
    public static final ResourceLocation HELLO_CHANNEL          = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "hello");
    //? }*/
    //? }

    //? if >=1.20.5 {
    //? if fabric {
    /** S2C: server emits a named event to the page. */
    public record WebviewEmitS2CPayload(String eventName, String jsonPayload) implements CustomPayload {
        public static final CustomPayload.Id<WebviewEmitS2CPayload> ID =
                new CustomPayload.Id<>(EMIT_TO_PAGE_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebviewEmitS2CPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(MAX_EVENT_NAME_LENGTH), WebviewEmitS2CPayload::eventName,
                PacketCodecs.string(MAX_EVENT_DATA_LENGTH), WebviewEmitS2CPayload::jsonPayload,
                WebviewEmitS2CPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** C2S: page sends a named event to the server. */
    public record WebviewPageEventC2SPayload(String channel, String jsonPayload) implements CustomPayload {
        public static final CustomPayload.Id<WebviewPageEventC2SPayload> ID =
                new CustomPayload.Id<>(PAGE_EVENT_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebviewPageEventC2SPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(MAX_EVENT_NAME_LENGTH), WebviewPageEventC2SPayload::channel,
                PacketCodecs.string(MAX_EVENT_DATA_LENGTH), WebviewPageEventC2SPayload::jsonPayload,
                WebviewPageEventC2SPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** displayMode: 0 = GUI, 1 = HUD */
    public record OpenWebS2CPayload(int protocolVersion, int displayMode, String url) implements CustomPayload {
        public static final CustomPayload.Id<OpenWebS2CPayload> ID =
                new CustomPayload.Id<>(OPEN_WEB_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, OpenWebS2CPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT,
                OpenWebS2CPayload::protocolVersion,
                PacketCodecs.VAR_INT,
                OpenWebS2CPayload::displayMode,
                PacketCodecs.string(WebviewNetworking.MAX_URL_LENGTH),
                OpenWebS2CPayload::url,
                OpenWebS2CPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record WebUIMainMenuPayload(String url) implements CustomPayload {
        public static final CustomPayload.Id<WebUIMainMenuPayload> ID =
                new CustomPayload.Id<>(MAIN_MENU_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebUIMainMenuPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(WebviewNetworking.MAX_URL_LENGTH),
                WebUIMainMenuPayload::url,
                WebUIMainMenuPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C: sets (or clears) the entity context for the currently open GUI. entityJson == "null" clears it. */
    public record WebviewEntityContextS2CPayload(String entityJson) implements CustomPayload {
        public static final CustomPayload.Id<WebviewEntityContextS2CPayload> ID =
                new CustomPayload.Id<>(ENTITY_CONTEXT_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebviewEntityContextS2CPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(MAX_EVENT_DATA_LENGTH),
                WebviewEntityContextS2CPayload::entityJson,
                WebviewEntityContextS2CPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * S2C: the page that replaces the vanilla death screen, plus what killed the player.
     *
     * The url is pushed on join and on config reload with an empty info field, so the
     * client already knows whether to suppress the vanilla screen by the time someone
     * dies — deciding that at death time would race the vanilla death packet. On death
     * the same payload carries the info JSON.
     */
    public record WebviewDeathS2CPayload(String url, String infoJson) implements CustomPayload {
        public static final CustomPayload.Id<WebviewDeathS2CPayload> ID =
                new CustomPayload.Id<>(DEATH_SCREEN_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebviewDeathS2CPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(WebviewNetworking.MAX_URL_LENGTH),
                WebviewDeathS2CPayload::url,
                PacketCodecs.string(MAX_EVENT_DATA_LENGTH),
                WebviewDeathS2CPayload::infoJson,
                WebviewDeathS2CPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * S2C: what the server is running, sent the moment a player joins.
     *
     * The client cannot otherwise tell a server that speaks an older WebGUI protocol
     * from one that speaks none at all, and the loaders disagree about what happens
     * then: NeoForge refuses the connection outright with a message about its own
     * version, Fabric lets the player in and silently drops every packet. Either way
     * the player is left guessing. This says plainly who is running what.
     */
    public record WebviewHelloS2CPayload(int protocolVersion, String modVersion) implements CustomPayload {
        public static final CustomPayload.Id<WebviewHelloS2CPayload> ID =
                new CustomPayload.Id<>(HELLO_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebviewHelloS2CPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT,
                WebviewHelloS2CPayload::protocolVersion,
                PacketCodecs.string(MAX_VERSION_LENGTH),
                WebviewHelloS2CPayload::modVersion,
                WebviewHelloS2CPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** S2C: newline-joined origins whose pages may run commands as the player. */
    public record WebviewTrustedOriginsS2CPayload(String origins) implements CustomPayload {
        public static final CustomPayload.Id<WebviewTrustedOriginsS2CPayload> ID =
                new CustomPayload.Id<>(TRUSTED_ORIGINS_CHANNEL);
        public static final PacketCodec<RegistryByteBuf, WebviewTrustedOriginsS2CPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(MAX_EVENT_DATA_LENGTH),
                WebviewTrustedOriginsS2CPayload::origins,
                WebviewTrustedOriginsS2CPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    //? } else {
    /*// S2C: server emits a named event to the page.
    public record WebviewEmitS2CPayload(String eventName, String jsonPayload) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewEmitS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(EMIT_TO_PAGE_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewEmitS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_NAME_LENGTH), WebviewEmitS2CPayload::eventName,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewEmitS2CPayload::jsonPayload,
                        WebviewEmitS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // C2S: page sends a named event to the server.
    public record WebviewPageEventC2SPayload(String channel, String jsonPayload) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewPageEventC2SPayload> TYPE =
                new CustomPacketPayload.Type<>(PAGE_EVENT_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewPageEventC2SPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_NAME_LENGTH), WebviewPageEventC2SPayload::channel,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH), WebviewPageEventC2SPayload::jsonPayload,
                        WebviewPageEventC2SPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // displayMode: 0 = GUI, 1 = HUD
    public record OpenWebS2CPayload(int protocolVersion, int displayMode, String url) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenWebS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(OPEN_WEB_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWebS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, OpenWebS2CPayload::protocolVersion,
                        ByteBufCodecs.VAR_INT, OpenWebS2CPayload::displayMode,
                        ByteBufCodecs.stringUtf8(WebviewNetworking.MAX_URL_LENGTH), OpenWebS2CPayload::url,
                        OpenWebS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WebUIMainMenuPayload(String url) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebUIMainMenuPayload> TYPE =
                new CustomPacketPayload.Type<>(MAIN_MENU_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebUIMainMenuPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(WebviewNetworking.MAX_URL_LENGTH), WebUIMainMenuPayload::url,
                        WebUIMainMenuPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // S2C: sets (or clears) the entity context for the currently open GUI.
    public record WebviewEntityContextS2CPayload(String entityJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewEntityContextS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(ENTITY_CONTEXT_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewEntityContextS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH),
                        WebviewEntityContextS2CPayload::entityJson,
                        WebviewEntityContextS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // S2C: the page that replaces the vanilla death screen, plus what killed the player.
    // The url arrives on join and on config reload with an empty info field so the client
    // already knows whether to suppress the vanilla screen before anyone dies; deciding
    // that at death time would race the vanilla death packet.
    public record WebviewDeathS2CPayload(String url, String infoJson) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewDeathS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(DEATH_SCREEN_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewDeathS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(WebviewNetworking.MAX_URL_LENGTH),
                        WebviewDeathS2CPayload::url,
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH),
                        WebviewDeathS2CPayload::infoJson,
                        WebviewDeathS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // S2C: what the server is running, sent the moment a player joins. The client cannot
    // otherwise tell a server that speaks an older WebGUI protocol from one that speaks
    // none at all, and the loaders disagree about what happens then: NeoForge refuses the
    // connection with a message about its own version, Fabric lets the player in and
    // silently drops every packet. This says plainly who is running what.
    public record WebviewHelloS2CPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewHelloS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(HELLO_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewHelloS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WebviewHelloS2CPayload::protocolVersion,
                        ByteBufCodecs.stringUtf8(MAX_VERSION_LENGTH), WebviewHelloS2CPayload::modVersion,
                        WebviewHelloS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // S2C: newline-joined origins whose pages may run commands as the player.
    public record WebviewTrustedOriginsS2CPayload(String origins) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WebviewTrustedOriginsS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(TRUSTED_ORIGINS_CHANNEL);
        public static final StreamCodec<RegistryFriendlyByteBuf, WebviewTrustedOriginsS2CPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_EVENT_DATA_LENGTH),
                        WebviewTrustedOriginsS2CPayload::origins,
                        WebviewTrustedOriginsS2CPayload::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }*/
    //? }
    //? }
}
