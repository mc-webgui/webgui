package land.webgui;

import land.webgui.compat.Mc;
import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;
//? if fabric {
//? if >=26 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///? } else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//? }
//? } else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;*/
//? }
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? }
//? if <=26.2 {
import org.lwjgl.glfw.GLFW;
//? }

public final class WebGUIKeys {

    // The keys this mod binds, in whatever numbering the game is speaking. Up to 26.2 that
    // is GLFW's; from 26.3 the window is SDL's and the numbers are its scan codes, so these
    // are taken from Minecraft rather than written down — the game knows which it is on.
    //? if >26.2 {
    /*static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYBOARD;
    static final int KEY_F6 = InputConstants.KEY_F6;
    static final int KEY_F12 = InputConstants.KEY_F12;
    static final int KEY_GRAVE = InputConstants.KEY_GRAVE;
    *///? } else {
    static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYSYM;
    static final int KEY_F6 = GLFW.GLFW_KEY_F6;
    static final int KEY_F12 = GLFW.GLFW_KEY_F12;
    static final int KEY_GRAVE = GLFW.GLFW_KEY_GRAVE_ACCENT;
    //? }
    private static KeyMapping mainMenu;
    private static KeyMapping hudInteractive;
    private static KeyMapping devtools;

    private WebGUIKeys() {}

    //? if fabric {
    /**
     * Registers a key with the loader.
     *
     * <p>A method of its own because the helper was renamed when Fabric API dropped "key
     * binding" for "key mapping" in 26 — and because a Stonecutter marker inside an
     * expression is not a thing the preprocessor can make sense of.
     */
    //? if >=26 {
    /*private static KeyMapping bind(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }
    *///? } else {
    private static KeyMapping bind(KeyMapping mapping) {
        return KeyBindingHelper.registerKeyBinding(mapping);
    }
    //? }
    //? }

    //? if fabric {
    public static void register() {
        //? if >=1.21.5 {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "web"));
        mainMenu = bind(new KeyMapping(
                "key.webgui.main_menu",
                KEY_TYPE,
                WebGUIKeys.KEY_F6,
                cat));
        hudInteractive = bind(new KeyMapping(
                "key.webgui.hud_interactive",
                KEY_TYPE,
                WebGUIKeys.KEY_GRAVE,
                cat));
        devtools = bind(new KeyMapping(
                "key.webgui.devtools",
                KEY_TYPE,
                WebGUIKeys.KEY_F12,
                cat));
        //? } else {
        /*mainMenu = bind(new KeyMapping(
                "key.webgui.main_menu",
                KEY_TYPE,
                WebGUIKeys.KEY_F6,
                WebGUIMod.MOD_ID + ".web"));
        hudInteractive = bind(new KeyMapping(
                "key.webgui.hud_interactive",
                KEY_TYPE,
                WebGUIKeys.KEY_GRAVE,
                WebGUIMod.MOD_ID + ".web"));
        devtools = bind(new KeyMapping(
                "key.webgui.devtools",
                KEY_TYPE,
                WebGUIKeys.KEY_F12,
                WebGUIMod.MOD_ID + ".web"));*/
        //? }
    }
    //? } else {
    /*public static void register(IEventBus modBus) {
        modBus.addListener(WebGUIKeys::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        //? if >=1.21.5 {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "web"));
        mainMenu = new KeyMapping("key.webgui.main_menu", KEY_TYPE, WebGUIKeys.KEY_F6, cat);
        hudInteractive = new KeyMapping("key.webgui.hud_interactive", KEY_TYPE, WebGUIKeys.KEY_GRAVE, cat);
        devtools = new KeyMapping("key.webgui.devtools", KEY_TYPE, WebGUIKeys.KEY_F12, cat);
        //? } else {
        mainMenu = new KeyMapping("key.webgui.main_menu", KEY_TYPE, WebGUIKeys.KEY_F6, WebGUIMod.MOD_ID + ".web");
        hudInteractive = new KeyMapping("key.webgui.hud_interactive", KEY_TYPE, WebGUIKeys.KEY_GRAVE, WebGUIMod.MOD_ID + ".web");
        devtools = new KeyMapping("key.webgui.devtools", KEY_TYPE, WebGUIKeys.KEY_F12, WebGUIMod.MOD_ID + ".web");
        //? }
        event.register(mainMenu);
        event.register(hudInteractive);
        event.register(devtools);
    }*/
    //? }

    public static void tick(Minecraft client) {
        while (mainMenu.consumeClick()) {
            tryOpenMainMenu(client);
        }
        while (hudInteractive.consumeClick()) {
            if (!WebHudOverlay.isHudVisible() || Mc.screen(client) != null) {
                continue;
            }
            WebHudOverlay.toggleInteractive(client);
        }
        while (devtools.consumeClick()) {
            // The screen has its own F12: a page in a screen gets the key before any key
            // binding does, so this is only ever the HUD.
            WebGUIDevToolsWindow.open(WebSession.hudBrowser());
        }
    }

    private static void tryOpenMainMenu(Minecraft client) {
        if (Mc.screen(client) instanceof WebViewScreen) {
            return;
        }
        if (!WebBrowser.ready()) {
            if (client.player != null) {
                //? if >=26 {
                /*client.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
                *///? } else {
                client.player.displayClientMessage(Component.translatable("message.webgui.mcef_not_ready"), false);
                //? }
            }
            return;
        }
        Mc.openScreen(client, new WebViewScreen(WebGUIMainMenuUrl.getUrl()));
    }
}
