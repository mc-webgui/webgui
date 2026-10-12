package land.webgui.mixin;

import land.webgui.browser.WebBrowser;
import land.webgui.WebHudOverlay;
import land.webgui.WebSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
//? if >=1.21.5 {
import net.minecraft.client.input.MouseButtonInfo;
//? }
//? if >26.2 {
/*import com.mojang.blaze3d.platform.InputConstants;
*///? } else {
import org.lwjgl.glfw.GLFW;
//? }
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    //? if >26.2 {
    /*private static final int PRESS = InputConstants.PRESS;
    private static final int RELEASE = InputConstants.RELEASE;
    *///? } else {
    private static final int PRESS = GLFW.GLFW_PRESS;
    private static final int RELEASE = GLFW.GLFW_RELEASE;
    //? }

    //? if >=1.21.5 {
    //? if >=1.21.5 {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
    //? } else {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, int button, int action, int mods, CallbackInfo ci) {
    *///? }
    //? } else {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, int button, int action, int mods, CallbackInfo ci) {
    *///? }
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        //? if >=1.21.5 {
        if (win.handle() != window) {
        //? } else {
        /*if (win.getWindow() != window) {
        *///? }
            return;
        }
        //? if >=1.21.5 {
        double mx = client.mouseHandler.getScaledXPos(win);
        double my = client.mouseHandler.getScaledYPos(win);
        //? } else {
        /*double mx = client.mouseHandler.xpos() / win.getGuiScale();
        double my = client.mouseHandler.ypos() / win.getGuiScale();
        *///? }
        if (!WebHudOverlay.containsMouse(mx, my, client)) {
            return;
        }
        WebBrowser browser = WebSession.browser();
        if (browser != null) {
            int lx = WebHudOverlay.toBrowserLocalX(mx, client);
            int ly = WebHudOverlay.toBrowserLocalY(my, client);
            browser.setFocus(true);
            //? if >=1.21.5 {
            //? if >=1.21.5 {
            if (action == PRESS) {
                browser.sendMousePress(lx, ly, rawButtonInfo.button());
            } else if (action == RELEASE) {
                browser.sendMouseRelease(lx, ly, rawButtonInfo.button());
            }
            //? } else {
            /*if (action == PRESS) {
                browser.sendMousePress(lx, ly, button);
            } else if (action == RELEASE) {
                browser.sendMouseRelease(lx, ly, button);
            }
            *///? }
            //? } else {
            /*if (action == PRESS) {
                browser.sendMousePress(lx, ly, button);
            } else if (action == RELEASE) {
                browser.sendMouseRelease(lx, ly, button);
            }*/
            //? }
        }
        ci.cancel();
    }

    //? if >26.2 {
    /*@Inject(method = "onMove", at = @At("HEAD"))
    private void webgui$moveToWebHud(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
    *///? } else {
    @Inject(method = "onMove", at = @At("HEAD"))
    private void webgui$moveToWebHud(long window, double x, double y, CallbackInfo ci) {
    //? }
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        //? if >=1.21.5 {
        if (win.handle() != window) {
        //? } else {
        /*if (win.getWindow() != window) {
        *///? }
            return;
        }
        WebBrowser browser = WebSession.browser();
        if (browser == null) {
            return;
        }
        double mx = x / win.getGuiScale();
        double my = y / win.getGuiScale();
        if (WebHudOverlay.containsMouse(mx, my, client)) {
            browser.sendMouseMove((int) x, (int) y);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void webgui$scrollToWebHudPseudoGui(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        //? if >=1.21.5 {
        if (win.handle() != window) {
        //? } else {
        /*if (win.getWindow() != window) {
        *///? }
            return;
        }
        //? if >=1.21.5 {
        double mx = client.mouseHandler.getScaledXPos(win);
        double my = client.mouseHandler.getScaledYPos(win);
        //? } else {
        /*double mx = client.mouseHandler.xpos() / win.getGuiScale();
        double my = client.mouseHandler.ypos() / win.getGuiScale();
        *///? }
        if (!WebHudOverlay.containsMouse(mx, my, client)) {
            return;
        }
        WebBrowser browser = WebSession.browser();
        if (browser == null) {
            return;
        }
        int lx = WebHudOverlay.toBrowserLocalX(mx, client);
        int ly = WebHudOverlay.toBrowserLocalY(my, client);
        browser.setFocus(true);
        browser.sendMouseWheel(lx, ly, (int) vertical, (int) horizontal);
        ci.cancel();
    }
}
