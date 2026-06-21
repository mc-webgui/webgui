package land.webgui;

//? if !fabric {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;*/
//? }

/**
 * S2C payload registration moved out of {@link WebGUIClient} so that the
 * dedicated server can register payload TYPES without triggering class load
 * of WebGUIClient (which imports {@code net.minecraft.client.Minecraft} and
 * other client-only classes — fatal {@code NoClassDefFoundError} on server).
 *
 * The handler bodies dispatch to {@code WebGUIClient.onXxxPayload(...)} via
 * INVOKESTATIC which the JVM resolves lazily on first invocation. With the
 * {@code FMLEnvironment.dist != Dist.CLIENT} guard the invocation never
 * happens on the server, so {@code WebGUIClient} is never loaded there.
 *
 * Fixes mc-webgui/webgui issue #9 (Screen class crash on Dedicated server).
 */
public final class WebGUIPayloadRegistration {

    private WebGUIPayloadRegistration() {}

    //? if !fabric {
    /*public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final var reg = event.registrar("1");

        reg.playToClient(WebviewPayloads.OpenWebS2CPayload.TYPE,
                WebviewPayloads.OpenWebS2CPayload.STREAM_CODEC,
                WebGUIPayloadRegistration::dispatchOpenWeb);

        reg.playToClient(WebviewPayloads.WebUIMainMenuPayload.TYPE,
                WebviewPayloads.WebUIMainMenuPayload.STREAM_CODEC,
                WebGUIPayloadRegistration::dispatchMainMenu);

        reg.playToClient(WebviewPayloads.WebviewEmitS2CPayload.TYPE,
                WebviewPayloads.WebviewEmitS2CPayload.STREAM_CODEC,
                WebGUIPayloadRegistration::dispatchEmit);

        reg.playToClient(WebviewPayloads.WebviewEntityContextS2CPayload.TYPE,
                WebviewPayloads.WebviewEntityContextS2CPayload.STREAM_CODEC,
                WebGUIPayloadRegistration::dispatchEntityContext);
    }

    private static void dispatchOpenWeb(WebviewPayloads.OpenWebS2CPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        WebGUIClient.onOpenWebPayload(payload, ctx);
    }

    private static void dispatchMainMenu(WebviewPayloads.WebUIMainMenuPayload payload, IPayloadContext ctx) {
        // MainMenuUrl is common-safe, but we keep the dist guard for symmetry.
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ctx.enqueueWork(() -> WebGUIMainMenuUrl.setUrl(payload.url()));
    }

    private static void dispatchEmit(WebviewPayloads.WebviewEmitS2CPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        WebGUIClient.onEmitPayload(payload, ctx);
    }

    private static void dispatchEntityContext(WebviewPayloads.WebviewEntityContextS2CPayload payload, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        WebGUIClient.onEntityContextPayload(payload, ctx);
    }*/
    //? }
}
