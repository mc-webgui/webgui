package land.webgui;

import land.webgui.server.WebGUIUpdateChecker;
import land.webgui.server.WebviewServerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebGUIMod implements ModInitializer {
    public static final String MOD_ID = "webgui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        WebviewNetworking.registerPayloadTypes();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            WebviewServerConfig.load();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WebGUIUpdateChecker.checkAsync();
        });
        WebviewCommands.register();
        WebviewJoinHud.register();
        LOGGER.info("WebGUI common init (S2C payloads, commands).");
    }
}
