package land.webgui;

import land.webgui.compat.Mc;
import land.webgui.browser.WebBrowser;
//? if fabric {
//? if >=26 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
*///? } else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//? }
import net.minecraft.client.Minecraft;
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines;
//? }
//? if >=26 {
/*// GuiGraphics is gone from the HUD path in 26: the game extracts render state first.
*///? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
//? if >=1.20.5 {
import net.minecraft.client.DeltaTracker;
//? }
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? }
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? }
import net.minecraft.network.chat.Component;
//? } else {
/*import net.minecraft.client.Minecraft;
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
//? }
//? if >=26 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
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

    public static boolean shouldDeliverHudBrowserInput(Minecraft client) {
        if (client == null || !WebBrowser.ready()) {
            return false;
        }
        boolean hasGui1 = Mc.screen(client) != null;
        if (!hudVisible || !hudInteractive || hasGui1) {
            return false;
        }
        if (WebSession.mode() != WebSession.Mode.HUD_OVERLAY) {
            return false;
        }
        return WebSession.browser() != null;
    }

    public static boolean shouldForwardHudArrowKeys(Minecraft client) {
        if (client == null || !WebBrowser.ready()) {
            return false;
        }
        boolean hasGui2 = Mc.screen(client) != null;
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

    public static boolean shouldBlockVanillaWorldInteractions(Minecraft client) {
        if (client == null || Mc.screen(client) != null) {
            return false;
        }
        if (!hudVisible || !hudInteractive) {
            return false;
        }
        return client.level != null;
    }

    public static void tickCursor(Minecraft client) {
        if (Mc.screen(client) != null) {
            return;
        }
        if (!hudVisible) {
            if (cursorUnlockedForWebHud) {
                client.mouseHandler.grabMouse();
                cursorUnlockedForWebHud = false;
            }
            return;
        }
        if (hudInteractive) {
            client.mouseHandler.releaseMouse();
            cursorUnlockedForWebHud = true;
            return;
        }
        if (cursorUnlockedForWebHud) {
            client.mouseHandler.grabMouse();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void applyServerOpen(Minecraft client, String url) {
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (Mc.screen(client) != null) {
            Mc.openScreen(client, null);
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

    public static void toggleHud(Minecraft client) {
        if (Mc.screen(client) instanceof WebViewScreen) {
            return;
        }
        if (!WebBrowser.ready()) {
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
                client.mouseHandler.grabMouse();
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

    public static void toggleInteractive(Minecraft client) {
        if (!WebBrowser.ready()) {
            return;
        }
        boolean hasGui7 = Mc.screen(client) != null;
        if (!hudVisible || hasGui7) {
            return;
        }
        hudInteractive = !hudInteractive;
        if (hudInteractive) {
            client.mouseHandler.releaseMouse();
            cursorUnlockedForWebHud = true;
        } else if (cursorUnlockedForWebHud) {
            client.mouseHandler.grabMouse();
            cursorUnlockedForWebHud = false;
        }
    }

    public static boolean isHudPageReady() {
        return hudPageReady;
    }

    /**
     * Clears all HUD overlay state. Called when leaving a world (disconnect / exit to title)
     * so a server-opened HUD does not linger on the main menu. The owning browser is closed
     * separately via {@link WebSession#dispose()}.
     */
    public static void reset() {
        hudVisible = false;
        hudPageReady = false;
        hudInteractive = false;
        restoreHudAfterGuiClose = false;
        cursorUnlockedForWebHud = false;
        lastPixelW = -1;
        lastPixelH = -1;
    }

    public static void onGuiOpened() {
        // Only decided when a gui is opening over the hud. A gui replacing another gui
        // runs this too, and by then the mode is already GUI_SCREEN, so recomputing it
        // here answered "there is no hud to put back" and threw the parked one away.
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY) {
            restoreHudAfterGuiClose = hudVisible;
        }
        hudInteractive = false;
        lastPixelW = -1;
        lastPixelH = -1;
        Minecraft c = Minecraft.getInstance();
        if (cursorUnlockedForWebHud && c != null) {
            c.mouseHandler.grabMouse();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void onGuiClosed(Minecraft client) {
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

    static void onHudBrowserLoadStart(WebBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && browser == WebSession.browser()) {
            hudPageReady = false;
        }
    }

    static void onHudBrowserLoadFinished(WebBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.HUD_OVERLAY && browser == WebSession.browser()) {
            hudPageReady = true;
            WebViewLayout.applyZoom(browser);
        }
    }

    //? if fabric {
    //? if >=26 {
    /*public static void register() {
        // Not an event any more: 26 keeps an ordered list of HUD elements, and a mod adds
        // one rather than hooking the whole HUD being drawn. Last, so the page sits over
        // the game's own bars rather than under them.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(WebGUIMod.MOD_ID, "page"),
                WebHudOverlay::onHudRender);
    }
    *///? } else {
    public static void register() {
        HudRenderCallback.EVENT.register(WebHudOverlay::onHudRender);
    }
    //? }
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebHudOverlay::onHudRender);
    }*/
    //? }

    //? if fabric {
    //? if >=26 {
    /*private static void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
    *///? } else {
    //? if >=1.20.5 {
    private static void onHudRender(GuiGraphics context, DeltaTracker tickCounter) {
    //? } else {
    /*private static void onHudRender(GuiGraphics context, float tickDelta) {*/
    //? }
    //? }
    //? } else {
    /*//? if >=1.20.5 {
    private static void onHudRender(RenderGuiEvent.Post event) {
        //? if >=26 {
        GuiGraphicsExtractor context = event.getGuiGraphics();
        //? } else {
        GuiGraphics context = event.getGuiGraphics();
        //? }
    //? } else {
    private static void onHudRender(RenderGuiEvent.Post event) {
        GuiGraphics context = event.getGuiGraphics();
    //? }*/
    //? }
        if (!hudVisible) {
            return;
        }
        WebBrowser browser = WebSession.hudBrowser();
        if (browser == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();

        resizeBrowser(client);

        if (!hudPageReady) {
            return;
        }
        if (!browser.isTextureReady()) return;
        //? if >=1.21.5 {
        net.minecraft.resources.Identifier tex = browser.getTextureIdentifier();
        //? } else {
        /*net.minecraft.resources.ResourceLocation tex = browser.getTextureIdentifier();
        *///? }
        if (tex == null) return;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        //? if >=1.21.5 {
        context.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, sw, sh, sw, sh);
        //? } else {
        /*context.blit(tex, 0, 0, 0f, 0f, sw, sh, sw, sh);
        *///? }
        // Only while the HUD holds the mouse, or a player looking around gets the page's cursor.
        //? if >26.2 {
        /*if (hudInteractive) {
            browser.requestCursor(context);
        }
        *///? }
    }

    public static boolean containsMouse(double mouseX, double mouseY, Minecraft client) {
        if (!hudVisible) {
            return false;
        }
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        return mouseX >= 0 && mouseY >= 0 && mouseX < sw && mouseY < sh;
    }

    public static int toBrowserLocalX(double mouseX, Minecraft client) {
        int sw = client.getWindow().getGuiScaledWidth();
        return (int) Math.round(mouseX * browserViewSize(client)[0] / (double) Math.max(1, sw));
    }

    public static int toBrowserLocalY(double mouseY, Minecraft client) {
        int sh = client.getWindow().getGuiScaledHeight();
        return (int) Math.round(mouseY * browserViewSize(client)[1] / (double) Math.max(1, sh));
    }

    static void resizeBrowser(Minecraft client) {
        WebBrowser browser = WebSession.hudBrowser();
        if (browser == null) {
            return;
        }
        int[] size = browserViewSize(client);
        int pxW = size[0];
        int pxH = size[1];
        if (pxW != lastPixelW || pxH != lastPixelH) {
            browser.resize(pxW, pxH);
            WebViewLayout.applyZoom(browser);
            lastPixelW = pxW;
            lastPixelH = pxH;
        }
    }

    // Browser view size = physical framebuffer px; page zoom keeps the CSS viewport logical.
    private static int[] browserViewSize(Minecraft client) {
        var window = client.getWindow();
        return WebViewLayout.browserSize(window.getWidth(), window.getHeight());
    }

    private static void notifyMcefMissing(Minecraft client) {
        if (client.player != null) {
            //? if >=26 {
            /*client.player.sendSystemMessage(
                    Component.translatable("message.webgui.mcef_not_ready"));
            *///? } else {
            client.player.displayClientMessage(
                    Component.translatable("message.webgui.mcef_not_ready"), false);
            //? }
        }
    }
}
