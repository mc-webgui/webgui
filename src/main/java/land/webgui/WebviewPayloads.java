package land.webgui;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class WebviewPayloads {
    private WebviewPayloads() {}

    /** displayMode: 0 = GUI, 1 = HUD */
    public record OpenWebS2CPayload(int protocolVersion, int displayMode, String url) implements CustomPayload {
        public static final CustomPayload.Id<OpenWebS2CPayload> ID =
                new CustomPayload.Id<>(Identifier.of(WebGUIMod.MOD_ID, "open_web"));
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
                new CustomPayload.Id<>(Identifier.of(WebGUIMod.MOD_ID, "set_main_menu"));
        public static final PacketCodec<RegistryByteBuf, WebUIMainMenuPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(WebviewNetworking.MAX_URL_LENGTH),
                WebUIMainMenuPayload::url,
                WebUIMainMenuPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
