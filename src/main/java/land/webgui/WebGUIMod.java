package land.webgui;

import land.webgui.server.WebGUIUpdateChecker;
import land.webgui.server.WebviewServerConfig;
//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
//? } else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;*/
//? }
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if !fabric {
/*@Mod(WebGUIMod.MOD_ID)*/
//? }
public final class WebGUIMod
        //? if fabric {
        implements ModInitializer
        //? }
{
    public static final String MOD_ID = "webgui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    //? if fabric {
    @Override
    public void onInitialize() {
        WebviewNetworking.registerPayloadTypes();
        WebviewNetworking.registerServerReceivers();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            WebviewServerConfig.load();
            EntityBindingStore.load();
        });
        EntityInteractionListener.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WebGUIUpdateChecker.checkAsync();
        });
        WebviewCommands.register();
        WebviewJoinHud.register();
        LOGGER.info("WebGUI common init (S2C payloads, commands).");
    }
    //? } else {
    /*public WebGUIMod(IEventBus modBus) {
        WebviewNetworking.registerPayloadTypes(modBus);
        // S2C payloads must be registered on BOTH sides: the server needs them to
        // negotiate and send, the client to receive. The handlers only ever run
        // client-side, so this is safe on a dedicated server.
        modBus.addListener(WebGUIClient::onRegisterPayloads);
        EntityInteractionListener.register();
        WebviewCommands.register();
        WebviewJoinHud.register();
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        //? if >=1.21.5 {
        Dist dist = FMLEnvironment.getDist();
        //? } else {
        Dist dist = FMLEnvironment.dist;
        //? }
        if (dist == Dist.CLIENT) {
            WebGUIClient.initClient(modBus);
        }
        LOGGER.info("WebGUI common init (S2C payloads, commands).");
    }

    private void onServerStarting(ServerStartingEvent event) {
        WebviewServerConfig.load();
        EntityBindingStore.load();
    }

    private void onServerStarted(ServerStartedEvent event) {
        WebGUIUpdateChecker.checkAsync();
    }*/
    //? }
}
