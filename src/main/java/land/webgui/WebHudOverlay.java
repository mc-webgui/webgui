package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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

    public static boolean shouldDeliverHudBrowserInput(MinecraftClient client) {
        if (client == null || !MCEF.isInitialized()) {
            return false;
        }
        if (!hudVisible || !hudInteractive || client.currentScreen != null) {
            return false;
        }
        if (WebSession.mode() != WebSession.Mode.HUD_OVERLAY) {
            return false;
        }
        return WebSession.browser() != null;
    }

    public static boolean shouldForwardHudArrowKeys(MinecraftClient client) {
        if (client == null || !MCEF.isInitialized()) {
            return false;
        }
        if (!hudVisible || client.currentScreen != null) {
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

    public static boolean shouldBlockVanillaWorldInteractions(MinecraftClient client) {
        if (client == null || client.currentScreen != null) {
            return false;
        }
        if (!hudVisible || !hudInteractive) {
            return false;
        }
        return client.world != null && client.world.isClient();
    }

    public static void tickCursor(MinecraftClient client) {
        if (client.currentScreen != null) {
            return;
        }
        if (!hudVisible) {
            if (cursorUnlockedForWebHud) {
                client.mouse.lockCursor();
                cursorUnlockedForWebHud = false;
            }
            return;
        }
        if (hudInteractive) {
            client.mouse.unlockCursor();
            cursorUnlockedForWebHud = true;
            return;
        }
        if (cursorUnlockedForWebHud) {
            client.mouse.lockCursor();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void applyServerOpen(MinecraftClient client, String url) {
        String u = url == null || url.isBlank() ? StartUrls.primary() : url;
        if (client.currentScreen != null) {
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

    public static void toggleHud(MinecraftClient client) {
        if (client.currentScreen instanceof WebViewScreen) {
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
                client.mouse.lockCursor();
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

    public static void toggleInteractive(MinecraftClient client) {
        if (!MCEF.isInitialized()) {
            return;
        }
        if (!hudVisible || client.currentScreen != null) {
            return;
        }
        hudInteractive = !hudInteractive;
        if (hudInteractive) {
            client.mouse.unlockCursor();
            cursorUnlockedForWebHud = true;
        } else if (cursorUnlockedForWebHud) {
            client.mouse.lockCursor();
            cursorUnlockedForWebHud = false;
        }
    }

    public static boolean isHudPageReady() {
        return hudPageReady;
    }

    public static void onGuiOpened() {
        restoreHudAfterGuiClose = hudVisible && WebSession.mode() == WebSession.Mode.HUD_OVERLAY;
        hudInteractive = false;
        lastPixelW = -1;
        lastPixelH = -1;
        MinecraftClient c = MinecraftClient.getInstance();
        if (cursorUnlockedForWebHud && c != null) {
            c.mouse.lockCursor();
            cursorUnlockedForWebHud = false;
        }
    }

    public static void onGuiClosed(MinecraftClient client) {
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

    public static void register() {
        HudRenderCallback.EVENT.register(WebHudOverlay::onHudRender);
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!hudVisible) {
            return;
        }
        MCEFBrowser browser = WebSession.hudBrowser();
        if (browser == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();

        resizeBrowser(client);

        if (!hudPageReady) {
            return;
        }
        if (!browser.isTextureReady()) {
            return;
        }
        Identifier tex = browser.getTextureIdentifier();
        if (tex == null) {
            return;
        }

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                tex,
                0,
                0,
                0f,
                0f,
                sw,
                sh,
                sw,
                sh);

    }

    public static boolean containsMouse(double mouseX, double mouseY, MinecraftClient client) {
        if (!hudVisible) {
            return false;
        }
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        return mouseX >= 0 && mouseY >= 0 && mouseX < sw && mouseY < sh;
    }

    public static int toBrowserLocalX(double mouseX, MinecraftClient client) {
        return (int) (mouseX * client.getWindow().getScaleFactor());
    }

    public static int toBrowserLocalY(double mouseY, MinecraftClient client) {
        return (int) (mouseY * client.getWindow().getScaleFactor());
    }

    static void resizeBrowser(MinecraftClient client) {
        MCEFBrowser browser = WebSession.hudBrowser();
        if (browser == null) {
            return;
        }
        // Use exact framebuffer dimensions — avoids 1-px mismatch from (int)(scaledW * scale)
        int pxW = client.getWindow().getWidth();
        int pxH = client.getWindow().getHeight();
        if (pxW != lastPixelW || pxH != lastPixelH) {
            browser.resize(pxW, pxH);
            lastPixelW = pxW;
            lastPixelH = pxH;
        }
    }

    private static void notifyMcefMissing(MinecraftClient client) {
        if (client.player != null) {
            client.player.sendMessage(Text.translatable("message.webgui.mcef_not_ready"), false);
        }
    }
}
