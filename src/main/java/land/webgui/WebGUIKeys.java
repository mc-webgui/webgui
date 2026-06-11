package land.webgui;

import com.cinemamod.mcef.MCEF;
//? if fabric {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
//? } else {
/*import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;*/
//? }
//? if fabric {
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
//? if >=1.21.5 {
import net.minecraft.util.Identifier;
//? }
//? } else {
/*import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? }*/
//? }
import org.lwjgl.glfw.GLFW;

public final class WebGUIKeys {
    //? if fabric {
    private static KeyBinding mainMenu;
    private static KeyBinding hudInteractive;
    //? } else {
    /*private static KeyMapping mainMenu;
    private static KeyMapping hudInteractive;*/
    //? }

    private WebGUIKeys() {}

    //? if fabric {
    public static void register() {
        //? if >=1.21.5 {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(WebGUIMod.MOD_ID, "web"));
        mainMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.webgui.main_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                cat));
        hudInteractive = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.webgui.hud_interactive",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                cat));
        //? } else {
        /*mainMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.webgui.main_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                WebGUIMod.MOD_ID + ".web"));
        hudInteractive = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.webgui.hud_interactive",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
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
        mainMenu = new KeyMapping("key.webgui.main_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, cat);
        hudInteractive = new KeyMapping("key.webgui.hud_interactive", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, cat);
        //? } else {
        mainMenu = new KeyMapping("key.webgui.main_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, WebGUIMod.MOD_ID + ".web");
        hudInteractive = new KeyMapping("key.webgui.hud_interactive", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, WebGUIMod.MOD_ID + ".web");
        //? }
        event.register(mainMenu);
        event.register(hudInteractive);
    }*/
    //? }

    //? if fabric {
    public static void tick(MinecraftClient client) {
    //? } else {
    /*public static void tick(Minecraft client) {*/
    //? }
        //? if fabric {
        while (mainMenu.wasPressed()) {
        //? } else {
        /*while (mainMenu.consumeClick()) {*/
        //? }
            tryOpenMainMenu(client);
        }
        //? if fabric {
        while (hudInteractive.wasPressed()) {
            if (!WebHudOverlay.isHudVisible() || client.currentScreen != null) {
        //? } else {
        /*while (hudInteractive.consumeClick()) {
            if (!WebHudOverlay.isHudVisible() || client.screen != null) {*/
        //? }
                continue;
            }
            WebHudOverlay.toggleInteractive(client);
        }
    }

    //? if fabric {
    private static void tryOpenMainMenu(MinecraftClient client) {
        if (client.currentScreen instanceof WebViewScreen) {
            return;
        }
        if (!MCEF.isInitialized()) {
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("message.webgui.mcef_not_ready"), false);
            }
            return;
        }
        client.setScreen(new WebViewScreen(WebGUIMainMenuUrl.getUrl()));
    }
    //? } else {
    /*private static void tryOpenMainMenu(Minecraft client) {
        if (client.screen instanceof WebViewScreen) {
            return;
        }
        if (!MCEF.isInitialized()) {
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable("message.webgui.mcef_not_ready"), false);
            }
            return;
        }
        client.setScreen(new WebViewScreen(WebGUIMainMenuUrl.getUrl()));
    }*/
    //? }
}
