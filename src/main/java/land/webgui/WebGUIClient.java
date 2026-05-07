package land.webgui;

import com.cinemamod.mcef.MCEF;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class WebGUIClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MCEF.scheduleForInit(success -> {
            if (!success) {
                WebGUIMod.LOGGER.error("MCEF (Chromium) failed to initialize — web GUI will not work.");
                return;
            }
            MCEF.getClient().addDisplayHandler(new WebviewBrowserConsoleLogger());
            WebviewPageToClientBridge.register();
            WebviewPageLoadHooks.register();
            WebGUIMod.LOGGER.info("WebGUI bridge ready (console log, page↔game, client data).");
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.OpenWebS2CPayload.ID, (payload, context) -> {
            if (payload.protocolVersion() != WebviewNetworking.PROTOCOL_VERSION) {
                return;
            }
            context.client().execute(() -> handleOpenPayload(context.client(), payload.displayMode(), payload.url()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WebviewPayloads.WebUIMainMenuPayload.ID, (payload, context) -> {
            context.client().execute(() -> WebGUIMainMenuUrl.setUrl(payload.url()));
        });

        WebGUIKeys.register();
        WebHudOverlay.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WebHudOverlay.tickCursor(client);
            WebGUIKeys.tick(client);
            WebviewClientBridge.tick(client);
        });
    }

    private static void handleOpenPayload(net.minecraft.client.MinecraftClient client, int mode, String url) {
        if (!MCEF.isInitialized()) {
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.translatable("message.webgui.mcef_not_ready"), false);
            }
            return;
        }
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (mode == WebviewNetworking.MODE_GUI) {
            client.setScreen(new WebViewScreen(u));
        } else if (mode == WebviewNetworking.MODE_HUD) {
            WebHudOverlay.applyServerOpen(client, u);
        }
    }
}
