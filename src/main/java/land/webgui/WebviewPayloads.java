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

    // Channel identifiers — used in both legacy (1.20.1) and modern networking
    //? if fabric {
    public static final Identifier OPEN_WEB_CHANNEL       = Identifier.of(WebGUIMod.MOD_ID, "open_web");
    public static final Identifier MAIN_MENU_CHANNEL      = Identifier.of(WebGUIMod.MOD_ID, "set_main_menu");
    public static final Identifier EMIT_TO_PAGE_CHANNEL   = Identifier.of(WebGUIMod.MOD_ID, "emit_to_page");
    public static final Identifier PAGE_EVENT_CHANNEL     = Identifier.of(WebGUIMod.MOD_ID, "page_event");
    public static final Identifier ENTITY_CONTEXT_CHANNEL = Identifier.of(WebGUIMod.MOD_ID, "entity_context");
    //? } else {
    /*//? if >=1.21.5 {
    public static final Identifier OPEN_WEB_CHANNEL       = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "open_web");
    public static final Identifier MAIN_MENU_CHANNEL      = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "set_main_menu");
    public static final Identifier EMIT_TO_PAGE_CHANNEL   = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "emit_to_page");
    public static final Identifier PAGE_EVENT_CHANNEL     = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "page_event");
    public static final Identifier ENTITY_CONTEXT_CHANNEL = Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "entity_context");
    //? } else {
    public static final ResourceLocation OPEN_WEB_CHANNEL       = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "open_web");
    public static final ResourceLocation MAIN_MENU_CHANNEL      = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "set_main_menu");
    public static final ResourceLocation EMIT_TO_PAGE_CHANNEL   = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "emit_to_page");
    public static final ResourceLocation PAGE_EVENT_CHANNEL     = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "page_event");
    public static final ResourceLocation ENTITY_CONTEXT_CHANNEL = ResourceLocation.fromNamespaceAndPath(WebGUIMod.MOD_ID, "entity_context");
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
    }*/
    //? }
    //? }
}
