package land.webgui;

import land.webgui.browser.WebBrowser;
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//? }
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;
*///? }

public class WebViewScreen extends Screen {

    private static boolean guiPageReady;
    private final String initialUrl;
    private WebBrowser browser;

    public WebViewScreen(String startUrl) {
        super(Component.translatable("screen.webgui.title"));
        this.initialUrl = startUrl == null || startUrl.isBlank() ? StartUrls.primary() : startUrl;
    }

    @Override
    protected void init() {
        super.init();
        if (!WebBrowser.ready()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                //? if >=26 {
                /*this.minecraft.player.sendSystemMessage(Component.translatable("message.webgui.mcef_not_ready"));
                *///? } else {
                this.minecraft.player.displayClientMessage(Component.translatable("message.webgui.mcef_not_ready"), false);
                //? }
            }
            this.onClose();
            return;
        }

        if (browser != null) {
            resizeBrowser();
            return;
        }

        WebHudOverlay.onGuiOpened();
        guiPageReady = false;
        browser = WebSession.openForGui(initialUrl);
        resizeBrowser();
        land.webgui.compat.TextInput.start(this);
    }

    @Override
    public void removed() {
        land.webgui.compat.TextInput.stop(this);
        super.removed();
    }

    private int getBrowserWidth() {
        return Math.max(1, this.width);
    }

    private int getBrowserHeight() {
        return Math.max(1, this.height);
    }

    private boolean isInBrowserBounds(double x, double y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height;
    }

    // Browser view size in logical window points, independent of the GUI Scale setting.
    private int[] browserViewSize() {
        var window = this.minecraft.getWindow();
        return WebViewLayout.browserSize(window.getWidth(), window.getHeight());
    }

    private int browserLocalMouseX(double x) {
        return (int) Math.round(x * browserViewSize()[0] / (double) getBrowserWidth());
    }

    private int browserLocalMouseY(double y) {
        return (int) Math.round(y * browserViewSize()[1] / (double) getBrowserHeight());
    }

    private void resizeBrowser() {
        if (browser != null) {
            int[] size = browserViewSize();
            browser.resize(size[0], size[1]);
            WebViewLayout.applyZoom(browser);
        }
    }

    //? if >=1.21.5 {
    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
    }
    //? } else {
    /*@Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(
            minecraft,
            width, height);
        resizeBrowser();
    }*/
    //? }

    @Override
    public void onClose() {
        guiPageReady = false;
        // Escaping a death page that never loaded has to respawn, or the player
        // is dropped into the world still dead with nothing to click.
        boolean respawnOnClose = WebGUIDeathScreen.active() && WebGUIDeathScreen.loadFailed();
        WebGUIDeathScreen.setActive(false);
        if (respawnOnClose && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.respawn();
        }
        WebSession.closeGuiAndRestoreHud();
        super.onClose();
        if (this.minecraft != null) {
            WebHudOverlay.onGuiClosed(this.minecraft);
        }
    }

    static void onGuiBrowserLoadStart(WebBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.GUI_SCREEN && browser == WebSession.browser()) {
            guiPageReady = false;
        }
    }

    static void onGuiBrowserLoadFinished(WebBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.GUI_SCREEN && browser == WebSession.browser()) {
            guiPageReady = true;
            WebViewLayout.applyZoom(browser);
        }
    }

    @Override
    //? if >=26 {
    /*public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    *///? } else {
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
    //? }
        if (browser != null && guiPageReady && browser.isTextureReady()) {
            //? if >=1.21.5 {
            net.minecraft.resources.Identifier textureLocation = browser.getTextureIdentifier();
            //? } else {
            /*net.minecraft.resources.ResourceLocation textureLocation = browser.getTextureIdentifier();
            *///? }
            if (textureLocation != null) {
                int fw = getBrowserWidth();
                int fh = getBrowserHeight();
                //? if >=1.21.5 {
                context.blit(RenderPipelines.GUI_TEXTURED, textureLocation, 0, 0, 0f, 0f, fw, fh, fw, fh);
                //? } else {
                /*context.blit(textureLocation, 0, 0, 0f, 0f, fw, fh, fw, fh);
                *///? }
            }
        }
        // Every frame: the game takes the last request made during one and ignores the rest.
        //? if >26.2 {
        /*if (browser != null) {
            browser.requestCursor(context);
        }
        *///? }
    }

    //? if >=1.20.5 {
    @Override
    //? if >=26 {
    /*public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
    *///? } else {
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
    //? }
        // no darkening - web fills the entire screen
    }
    //? } else {
    /*//? if fabric {
    @Override
    public void renderBackground(GuiGraphics context) {
        // no darkening - web fills the entire screen
    }
    //? } else {
    @Override
    public void renderBackground(GuiGraphics context) {
        // no darkening - web fills the entire screen
    }
    //? }*/
    //? }

    //? if >=1.21.5 {
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (browser == null || !isInBrowserBounds(click.x(), click.y())) return false;
        browser.sendMousePress(browserLocalMouseX(click.x()), browserLocalMouseY(click.y()), click.button());
        browser.setFocus(true);
        return true;
    }
    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (browser == null) return false;
        browser.sendMouseRelease(browserLocalMouseX(click.x()), browserLocalMouseY(click.y()), click.button());
        browser.setFocus(true);
        return true;
    }
    //? } else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser == null || !isInBrowserBounds(mouseX, mouseY)) {
            return false;
        }
        browser.sendMousePress(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY), button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser == null) {
            return false;
        }
        browser.sendMouseRelease(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY), button);
        browser.setFocus(true);
        return true;
    }*/
    //? }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && isInBrowserBounds(mouseX, mouseY)) {
            browser.sendMouseMove(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    //? if >=1.20.5 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser == null || !isInBrowserBounds(mouseX, mouseY)) {
            return false;
        }
        browser.sendMouseWheel(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY), verticalAmount, 0);
        return true;
    }
    //? } else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (browser == null || !isInBrowserBounds(mouseX, mouseY)) {
            return false;
        }
        browser.sendMouseWheel(browserLocalMouseX(mouseX), browserLocalMouseY(mouseY), amount, 0);
        return true;
    }*/
    //? }

    //? if >=1.21.5 {
    @Override
    public boolean keyPressed(KeyEvent input) {
        if (super.keyPressed(input)) return true;
        if (browser == null) return false;
        if (input.key() == WebGUIKeys.KEY_F12) {
            WebGUIDevToolsWindow.open(browser);
            return true;
        }
        //? if >26.2 {
        /*browser.sendKeyPress(input.key(), input.key(), input.modifiers());
        *///? } else {
        browser.sendKeyPress(input.key(), input.scancode(), input.modifiers());
        //? }
        browser.setFocus(true);
        return true;
    }
    @Override
    public boolean keyReleased(KeyEvent input) {
        if (super.keyReleased(input)) return true;
        if (browser == null) return false;
        //? if >26.2 {
        /*browser.sendKeyRelease(input.key(), input.key(), input.modifiers());
        *///? } else {
        browser.sendKeyRelease(input.key(), input.scancode(), input.modifiers());
        //? }
        browser.setFocus(true);
        return true;
    }
    @Override
    public boolean charTyped(CharacterEvent input) {
        if (super.charTyped(input)) return true;
        if (browser == null || !input.isAllowedChatCharacter()) return false;
        int cp = input.codepoint();
        if (cp <= 0 || cp > 0xFFFF) return false;
        //? if >=26 {
        /*browser.sendKeyTyped((char) cp, 0);
        *///? } else {
        browser.sendKeyTyped((char) cp, input.modifiers());
        //? }
        browser.setFocus(true);
        return true;
    }
    //? } else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (browser == null) {
            return false;
        }
        if (keyCode == WebGUIKeys.KEY_F12) {
            WebGUIDevToolsWindow.open(browser);
            return true;
        }
        browser.sendKeyPress(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (super.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (browser == null) {
            return false;
        }
        browser.sendKeyRelease(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (super.charTyped(chr, modifiers)) {
            return true;
        }
        if (browser == null) {
            return false;
        }
        browser.sendKeyTyped(chr, modifiers);
        browser.setFocus(true);
        return true;
    }*/
    //? }

    //? if fabric {
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    //? }

    /**
     * Escape closes a normal web GUI, but not one standing in for the death
     * screen — vanilla does not let a dead player walk away from it either, and
     * doing so would drop them into the world with no way back to respawn.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return !WebGUIDeathScreen.active() || WebGUIDeathScreen.loadFailed();
    }
}
