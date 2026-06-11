package land.webgui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
//? if >=1.21.5 {
//? if fabric {
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
//? } else {
/*import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;*/
//? }
//? }
//? if fabric {
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
//? } else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=1.21.5 {
import net.minecraft.resources.Identifier;
//? } else {
import net.minecraft.resources.ResourceLocation;
//? }*/
//? }

//? if fabric {
public class WebViewScreen extends Screen {
//? } else {
/*public class WebViewScreen extends Screen {*/
//? }

    private static boolean guiPageReady;
    private final String initialUrl;
    private MCEFBrowser browser;

    //? if fabric {
    public WebViewScreen(String startUrl) {
        super(Text.translatable("screen.webgui.title"));
        this.initialUrl = startUrl == null || startUrl.isBlank() ? StartUrls.primary() : startUrl;
    }
    //? } else {
    /*public WebViewScreen(String startUrl) {
        super(Component.translatable("screen.webgui.title"));
        this.initialUrl = startUrl == null || startUrl.isBlank() ? StartUrls.primary() : startUrl;
    }*/
    //? }

    @Override
    protected void init() {
        super.init();
        if (!MCEF.isInitialized()) {
            //? if fabric {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.webgui.mcef_not_ready"), false);
            }
            //? } else {
            /*if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.webgui.mcef_not_ready"), false);
            }*/
            //? }
            //? if fabric {
            this.close();
            //? } else {
            /*this.onClose();*/
            //? }
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

    private int browserLocalMouseX(double x) {
        //? if fabric {
        return (int) (x * this.client.getWindow().getScaleFactor());
        //? } else {
        /*return (int) (x * this.minecraft.getWindow().getGuiScale());*/
        //? }
    }

    private int browserLocalMouseY(double y) {
        //? if fabric {
        return (int) (y * this.client.getWindow().getScaleFactor());
        //? } else {
        /*return (int) (y * this.minecraft.getWindow().getGuiScale());*/
        //? }
    }

    private void resizeBrowser() {
        //? if fabric {
        if (browser != null && this.client != null) {
            browser.resize(this.client.getWindow().getWidth(), this.client.getWindow().getHeight());
        }
        //? } else {
        /*if (browser != null && this.minecraft != null) {
            browser.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
        }*/
        //? }
    }

    //? if >=1.21.5 {
    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
    }
    //? } else {
    /*@Override
    //? if fabric {
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
    //? } else {
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
    //? }
        super.resize(
            //? if fabric {
            client,
            //? } else {
            minecraft,
            //? }
            width, height);
        resizeBrowser();
    }*/
    //? }

    //? if fabric {
    @Override
    public void close() {
        guiPageReady = false;
        WebSession.closeGuiAndRestoreHud();
        super.close();
        if (this.client != null) {
            WebHudOverlay.onGuiClosed(this.client);
        }
    }
    //? } else {
    /*@Override
    public void onClose() {
        guiPageReady = false;
        WebSession.closeGuiAndRestoreHud();
        super.onClose();
        if (this.minecraft != null) {
            WebHudOverlay.onGuiClosed(this.minecraft);
        }
    }*/
    //? }

    static void onGuiBrowserLoadStart(MCEFBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.GUI_SCREEN && browser == WebSession.browser()) {
            guiPageReady = false;
        }
    }

    static void onGuiBrowserLoadFinished(MCEFBrowser browser) {
        if (browser == null) {
            return;
        }
        if (WebSession.mode() == WebSession.Mode.GUI_SCREEN && browser == WebSession.browser()) {
            guiPageReady = true;
        }
    }

    //? if fabric {
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (browser != null && guiPageReady && browser.isTextureReady()) {
            //? if >=1.21.5 {
            Identifier textureLocation = browser.getTextureIdentifier();
            //? } else {
            /*Identifier textureLocation = browser.getTextureLocation();*/
            //? }
            if (textureLocation != null) {
                int fw = getBrowserWidth();
                int fh = getBrowserHeight();
                //? if >=1.21.5 {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, textureLocation, 0, 0, 0f, 0f, fw, fh, fw, fh);
                //? } else {
                /*context.drawTexture(textureLocation, 0, 0, 0, 0f, 0f, fw, fh, fw, fh);*/
                //? }
            }
        }
    }
    //? } else {
    /*@Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (browser != null && guiPageReady && browser.isTextureReady()) {
            //? if >=1.21.5 {
            net.minecraft.resources.Identifier textureLocation = browser.getTextureIdentifier();
            //? } else {
            net.minecraft.resources.ResourceLocation textureLocation = browser.getTextureLocation();
            //? }
            if (textureLocation != null) {
                int fw = getBrowserWidth();
                int fh = getBrowserHeight();
                //? if >=1.21.5 {
                context.blit(RenderPipelines.GUI_TEXTURED, textureLocation, 0, 0, 0f, 0f, fw, fh, fw, fh);
                //? } else {
                context.blit(textureLocation, 0, 0, 0f, 0f, fw, fh, fw, fh);
                //? }
            }
        }
    }*/
    //? }

    //? if >=1.20.5 {
    //? if fabric {
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // no darkening — web fills the entire screen
    }
    //? } else {
    /*@Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        // no darkening - web fills the entire screen
    }*/
    //? }
    //? } else {
    /*//? if fabric {
    @Override
    public void renderBackground(DrawContext context) {
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
    //? if fabric {
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (browser == null || !isInBrowserBounds(click.x(), click.y())) {
            return false;
        }
        browser.sendMousePress(browserLocalMouseX(click.x()), browserLocalMouseY(click.y()), click.button());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (browser == null) {
            return false;
        }
        browser.sendMouseRelease(browserLocalMouseX(click.x()), browserLocalMouseY(click.y()), click.button());
        browser.setFocus(true);
        return true;
    }
    //? } else {
    /*@Override
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
    }*/
    //? }
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
    //? if fabric {
    @Override
    public boolean keyPressed(KeyInput input) {
        if (super.keyPressed(input)) return true;
        if (browser == null) return false;
        browser.sendKeyPress(input.key(), input.scancode(), input.modifiers());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        if (super.keyReleased(input)) return true;
        if (browser == null) return false;
        browser.sendKeyRelease(input.key(), input.scancode(), input.modifiers());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (super.charTyped(input)) return true;
        if (browser == null || !input.isValidChar()) return false;
        int cp = input.codepoint();
        if (cp <= 0 || cp > 0xFFFF) return false;
        browser.sendKeyTyped((char) cp, input.modifiers());
        browser.setFocus(true);
        return true;
    }
    //? } else {
    /*@Override
    public boolean keyPressed(KeyEvent input) {
        if (super.keyPressed(input)) return true;
        if (browser == null) return false;
        browser.sendKeyPress(input.key(), input.scancode(), input.modifiers());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (super.keyReleased(input)) return true;
        if (browser == null) return false;
        browser.sendKeyRelease(input.key(), input.scancode(), input.modifiers());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (super.charTyped(input)) return true;
        if (browser == null || !input.isAllowedChatCharacter()) return false;
        int cp = input.codepoint();
        if (cp <= 0 || cp > 0xFFFF) return false;
        browser.sendKeyTyped((char) cp, input.modifiers());
        browser.setFocus(true);
        return true;
    }*/
    //? }
    //? } else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (browser == null) {
            return false;
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
    public boolean shouldPause() {
        return false;
    }
    //? }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
