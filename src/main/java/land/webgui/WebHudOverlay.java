package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
//? if fabric {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
//? if >=1.21.5 {
import net.minecraft.client.gl.RenderPipelines;
//? }
import net.minecraft.client.gui.DrawContext;
//? if >=1.20.5 {
import net.minecraft.client.render.RenderTickCounter;
//? }
//? if >=1.21.5 {
import net.minecraft.util.Identifier;
//? }
import net.minecraft.text.Text;
//? } else {
/*import net.minecraft.client.Minecraft;
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
//? }
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;*/
//? }

public final class WebHudOverlay {
    private static boolean hudVisible;
    private static boolean hudPageReady;
    private static boolean hudInteractive;
    private static int lastPixelW = -1;
    private static int lastPixelH = -1;
    private static boolean cursorUnlockedForWebHud;
    private static boolean restoreHudAfterGuiClose;

    private WebHudOverlay() {}

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static boolean isHudInteractive() {
        return hudInteractive;
    }

    public static void setHudInteractive(boolean value) {
        hudInteractive = value;
    }

    //? if fabric {
    public static boolean shouldDeliverHudBrowserInput(MinecraftClient client) {
    //? } else {
    /*public static boolean shouldDeliverHudBrowserInput(Minecraft client) {*/
    //? }
        if (client == null || !MCEF.isInitialized()) {
            return false;
        }
        //? if fabric {
        boolean hasGui1 = client.currentScreen != null;
        //? } else {
        /*boolean hasGui1 = client.screen != null;*/
        //? }
        if (!hudVisible || !hudInteractive || hasGui1) {
            return false;
        }
        if (WebSession.mode() != WebSession.Mode.HUD_OVERLAY) {
            return false;
        }
        return WebSession.browser() != null;
    }

    //? if fabric {
    public static boolean shouldForwardHudArrowKeys(MinecraftClient client) {
    //? } else {
    /*public static boolean shouldForwardHudArrowKeys(Minecraft client) {*/
    //? }
        if (client == null || !MCEF.isInitialized()) {
            return false;
        }
        //? if fabric {
        boolean hasGui2 = client.currentScreen != null;
        //? } else {
        /*boolean hasGui2 = client.screen != null;*/
        //? }
        if (!hudVisible || hasGui2) {
            return false;
        }
        if (hudInteractive) {
            return false;
        }
        if (WebSession.mode() != WebSession.Mode.HUD_OVERLAY) {
            return false;
        }
        return WebSession.browser() != null;
    }

    //? if fabric {
    public static boolean shouldBlockVanillaWorldInteractions(MinecraftClient client) {
    //? } else {
    /*public static boolean shouldBlockVanillaWorldInteractions(Minecraft client) {*/
    //? }
        //? if fabric {
        if (client == null || client.currentScreen != null) {
        //? } else {
        /*if (client == null || client.screen != null) {*/
        //? }
            return false;
        }
        if (!hudVisible || !hudInteractive) {
            return false;
        }
        //? if fabric {
        return client.world != null && client.world.isClient();
        //? } else {
        /*return client.level != null;*/
        //? }
    }

    //? if fabric {
    public static void tickCursor(MinecraftClient client) {
    //? } else {
    /*public static void tickCursor(Minecraft client) {*/
    //? }
        //? if fabric {
        if (client.currentScreen != null) {
        //? } else {
        /*if (client.screen != null) {*/
        //? }
            return;
        }
        if (!hudVisible) {
            if (cursorUnlockedForWebHud) {
                //? if fabric {
                client.mouse.lockCursor();
                //? } else {
                /*client.mouseHandler.grabMouse();*/
                //? }
                cursorUnlockedForWebHud = false;
            }
            return;
        }
        if (hudInteractive) {
            //? if fabric {
            client.mouse.unlockCursor();
            //? } else {
            /*client.mouseHandler.releaseMouse();*/
            //? }
            cursorUnlockedForWebHud = true;
            return;
        }
        if (cursorUnlockedForWebHud) {
            //? if fabric {
            client.mouse.lockCursor();
            //? } else {
            /*client.mouseHandler.grabMouse();*/
            //? }
            cursorUnlockedForWebHud = false;
        }
    }

    //? if fabric {
    public static void applyServerOpen(MinecraftClient client, String url) {
    //? } else {
    /*public static void applyServerOpen(Minecraft client, String url) {*/
    //? }
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        //? if fabric {
        if (client.currentScreen != null) {
        //? } else {
        /*if (client.screen != null) {*/
        //? }
            client.setScreen(null);
        }
        restoreHudAfterGuiClose = false;
        hudVisible = true;
        hudPageReady = false;
        hudInteractive = false;
        lastPixelW = -1;
        lastPixelH = -1;
        WebSession.openForHud(u);
        resizeBrowser(client);
    }

    //? if fabric {
    public static void toggleHud(MinecraftClient client) {
    //? } else {
    /*public static void toggleHud(Minecraft client) {*/
    //? }
        //? if fabric {
        if (client.currentScreen instanceof WebViewScreen) {
        //? } else {
        /*if (client.screen instanceof WebViewScreen) {*/
        //? }
            return;
        }
        if (!MCEF.isInitialized()) {
            notifyMcefMissing(client);
            return;
        }
        if (hudVisible) {
            hudVisible = false;
            hudPageReady = false;
            hudInteractive = false;
            WebSession.closeHudOnly();
            lastPixelW = -1;
            lastPixelH = -1;
            if (cursorUnlockedForWebHud) {
                //? if fabric {
                client.mouse.lockCursor();
                //? } else {
                /*client.mouseHandler.grabMouse();*/
                //? }
                cursorUnlockedForWebHud = false;
            }
        } else {
            hudVisible = true;
            hudPageReady = false;
            hudInteractive = false;
            WebSession.openForHud(StartUrls.primary());
            resizeBrowser(client);
        }
    }

    //? if fabric {
    public static void toggleInteractive(MinecraftClient client) {
    //? } else {
    /*public static void toggleInteractive(Minecraft client) {*/
    //? }
        if (!MCEF.isInitialized()) {
            return;
        }
        //? if fabric {
        boolean hasGui7 = client.currentScreen != null;
        //? } else {
        /*boolean hasGui7 = client.screen != null;*/
        //? }
        if (!hudVisible || hasGui7) {
            return;
        }
        hudInteractive = !hudInteractive;
        if (hudInteractive) {
            //? if fabric {
            client.mouse.unlockCursor();
            //? } else {
            /*client.mouseHandler.releaseMouse();*/
            //? }
            cursorUnlockedForWebHud = true;
        } else if (cursorUnlockedForWebHud) {
            //? if fabric {
            client.mouse.lockCursor();
            //? } else {
            /*client.mouseHandler.grabMouse();*/
            //? }
            cursorUnlockedForWebHud = false;
        }
    }

    public static boolean isHudPageReady() {
        return hudPageReady;
    }

    //? if fabric {
    public static void onGuiOpened() {
    //? } else {
    /*public static void onGuiOpened() {*/
    //? }
        restoreHudAfterGuiClose = hudVisible && WebSession.mode() == WebSession.Mode.HUD_OVERLAY;
        hudInteractive = false;
        lastPixelW = -1;
        lastPixelH = -1;
        //? if fabric {
        MinecraftClient c = MinecraftClient.getInstance();
        //? } else {
        /*Minecraft c = Minecraft.getInstance();*/
        //? }
        if (cursorUnlockedForWebHud && c != null) {
            //? if fabric {
            c.mouse.lockCursor();
            //? } else {
            /*c.mouseHandler.grabMouse();*/
            //? }
            cursorUnlockedForWebHud = false;
        }
    }

    //? if fabric {
    public static void onGuiClosed(MinecraftClient client) {
    //? } else {
    /*public static void onGuiClosed(Minecraft client) {*/
    //? }
        if (!restoreHudAfterGuiClose) {
            return;
        }
        restoreHudAfterGuiClose = false;
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && WebSession.browser() != null) {
            hudVisible = true;
            hudInteractive = false;
            lastPixelW = -1;
            lastPixelH = -1;
            resizeBrowser(client);
        }
    }

    static void onHudBrowserLoadStart(MCEFBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && browser == WebSession.browser()) {
            hudPageReady = false;
        }
    }

    static void onHudBrowserLoadFinished(MCEFBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && browser == WebSession.browser()) {
            hudPageReady = true;
        }
    }

    //? if fabric {
    public static void register() {
        HudRenderCallback.EVENT.register(WebHudOverlay::onHudRender);
    }
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebHudOverlay::onHudRender);
    }*/
    //? }

    //? if fabric {
    //? if >=1.20.5 {
    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
    //? } else {
    /*private static void onHudRender(DrawContext context, float tickDelta) {*/
    //? }
    //? } else {
    /*//? if >=1.20.5 {
    private static void onHudRender(RenderGuiEvent.Post event) {
        GuiGraphics context = event.getGuiGraphics();
    //? } else {
    private static void onHudRender(RenderGuiEvent.Post event) {
        GuiGraphics context = event.getGuiGraphics();
    //? }*/
    //? }
        if (!hudVisible) {
            return;
        }
        MCEFBrowser browser = WebSession.hudBrowser();
        if (browser == null) {
            return;
        }
        //? if fabric {
        MinecraftClient client = MinecraftClient.getInstance();
        //? } else {
        /*Minecraft client = Minecraft.getInstance();*/
        //? }

        resizeBrowser(client);

        if (!hudPageReady) {
            return;
        }
        //? if fabric {
        if (!browser.isTextureReady()) {
            return;
        }
        //? if >=1.21.5 {
        net.minecraft.util.Identifier tex = browser.getTextureIdentifier();
        //? } else {
        /*net.minecraft.util.Identifier tex = browser.getTextureLocation();*/
        //? }
        if (tex == null) {
            return;
        }
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        //? if >=1.21.5 {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, sw, sh, sw, sh);
        //? } else {
        /*context.drawTexture(tex, 0, 0, 0, 0f, 0f, sw, sh, sw, sh);*/
        //? }
        //? } else {
        /*if (!browser.isTextureReady()) return;
        //? if >=1.21.5 {
        net.minecraft.resources.Identifier tex = browser.getTextureIdentifier();
        //? } else {
        net.minecraft.resources.ResourceLocation tex = browser.getTextureLocation();
        //? }
        if (tex == null) return;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        //? if >=1.21.5 {
        context.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, sw, sh, sw, sh);
        //? } else {
        context.blit(tex, 0, 0, 0f, 0f, sw, sh, sw, sh);
        //? }*/
        //? }
    }

    //? if fabric {
    public static boolean containsMouse(double mouseX, double mouseY, MinecraftClient client) {
    //? } else {
    /*public static boolean containsMouse(double mouseX, double mouseY, Minecraft client) {*/
    //? }
        if (!hudVisible) {
            return false;
        }
        //? if fabric {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        //? } else {
        /*int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();*/
        //? }
        return mouseX >= 0 && mouseY >= 0 && mouseX < sw && mouseY < sh;
    }

    //? if fabric {
    public static int toBrowserLocalX(double mouseX, MinecraftClient client) {
    //? } else {
    /*public static int toBrowserLocalX(double mouseX, Minecraft client) {*/
    //? }
        //? if fabric {
        return (int) (mouseX * client.getWindow().getScaleFactor());
        //? } else {
        /*return (int) (mouseX * client.getWindow().getGuiScale());*/
        //? }
    }

    //? if fabric {
    public static int toBrowserLocalY(double mouseY, MinecraftClient client) {
    //? } else {
    /*public static int toBrowserLocalY(double mouseY, Minecraft client) {*/
    //? }
        //? if fabric {
        return (int) (mouseY * client.getWindow().getScaleFactor());
        //? } else {
        /*return (int) (mouseY * client.getWindow().getGuiScale());*/
        //? }
    }

    //? if fabric {
    static void resizeBrowser(MinecraftClient client) {
    //? } else {
    /*static void resizeBrowser(Minecraft client) {*/
    //? }
        MCEFBrowser browser = WebSession.hudBrowser();
        if (browser == null) {
            return;
        }
        //? if fabric {
        int pxW = client.getWindow().getWidth();
        int pxH = client.getWindow().getHeight();
        //? } else {
        /*int pxW = client.getWindow().getWidth();
        int pxH = client.getWindow().getHeight();*/
        //? }
        if (pxW != lastPixelW || pxH != lastPixelH) {
            browser.resize(pxW, pxH);
            lastPixelW = pxW;
            lastPixelH = pxH;
        }
    }

    //? if fabric {
    private static void notifyMcefMissing(MinecraftClient client) {
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("message.webgui.mcef_not_ready"), false);
        }
    }
    //? } else {
    /*private static void notifyMcefMissing(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.translatable("message.webgui.mcef_not_ready"), false);
        }
    }*/
    //? }
}
