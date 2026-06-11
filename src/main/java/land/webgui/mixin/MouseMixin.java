package land.webgui.mixin;

import com.cinemamod.mcef.MCEFBrowser;
import land.webgui.WebHudOverlay;
import land.webgui.WebSession;
//? if fabric {
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
//? } else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;*/
//? }
//? if >=1.21.5 {
//? if fabric {
import net.minecraft.client.input.MouseInput;
//? }
//? }
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if fabric {
@Mixin(Mouse.class)
//? } else {
/*@Mixin(MouseHandler.class)*/
//? }
public class MouseMixin {

    //? if >=1.21.5 {
    //? if fabric {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, MouseInput input, int action, CallbackInfo ci) {
    //? } else {
    /*@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, int button, int action, int mods, CallbackInfo ci) {*/
    //? }
    //? } else {
    /*@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, int button, int action, int mods, CallbackInfo ci) {*/
    //? }
        //? if fabric {
        MinecraftClient client = MinecraftClient.getInstance();
        //? } else {
        /*Minecraft client = Minecraft.getInstance();*/
        //? }
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        //? if fabric {
        if (win.getHandle() != window) {
        //? } else {
        /*//? if >=1.21.5 {
        if (win.handle() != window) {
        //? } else {
        if (win.getWindow() != window) {
        //? }*/
        //? }
            return;
        }
        //? if >=1.21.5 {
        //? if fabric {
        double mx = client.mouse.getScaledX(win);
        double my = client.mouse.getScaledY(win);
        //? } else {
        /*double mx = client.mouseHandler.getScaledXPos(win);
        double my = client.mouseHandler.getScaledYPos(win);*/
        //? }
        //? } else {
        //? if fabric {
        /*double mx = client.mouse.getX() / win.getScaleFactor();
        double my = client.mouse.getY() / win.getScaleFactor();*/
        //? } else {
        /*double mx = client.mouseHandler.xpos() / win.getGuiScale();
        double my = client.mouseHandler.ypos() / win.getGuiScale();*/
        //? }
        //? }
        if (!WebHudOverlay.containsMouse(mx, my, client)) {
            return;
        }
        MCEFBrowser browser = WebSession.browser();
        if (browser != null) {
            int lx = WebHudOverlay.toBrowserLocalX(mx, client);
            int ly = WebHudOverlay.toBrowserLocalY(my, client);
            browser.setFocus(true);
            //? if >=1.21.5 {
            //? if fabric {
            if (action == GLFW.GLFW_PRESS) {
                browser.sendMousePress(lx, ly, input.button());
            } else if (action == GLFW.GLFW_RELEASE) {
                browser.sendMouseRelease(lx, ly, input.button());
            }
            //? } else {
            /*if (action == GLFW.GLFW_PRESS) {
                browser.sendMousePress(lx, ly, button);
            } else if (action == GLFW.GLFW_RELEASE) {
                browser.sendMouseRelease(lx, ly, button);
            }*/
            //? }
            //? } else {
            /*if (action == GLFW.GLFW_PRESS) {
                browser.sendMousePress(lx, ly, button);
            } else if (action == GLFW.GLFW_RELEASE) {
                browser.sendMouseRelease(lx, ly, button);
            }*/
            //? }
        }
        ci.cancel();
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void webgui$moveToWebHud(long window, double x, double y, CallbackInfo ci) {
        //? if fabric {
        MinecraftClient client = MinecraftClient.getInstance();
        //? } else {
        /*Minecraft client = Minecraft.getInstance();*/
        //? }
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        //? if fabric {
        if (win.getHandle() != window) {
        //? } else {
        /*//? if >=1.21.5 {
        if (win.handle() != window) {
        //? } else {
        if (win.getWindow() != window) {
        //? }*/
        //? }
            return;
        }
        MCEFBrowser browser = WebSession.browser();
        if (browser == null) {
            return;
        }
        //? if fabric {
        double mx = x / win.getScaleFactor();
        double my = y / win.getScaleFactor();
        //? } else {
        /*double mx = x / win.getGuiScale();
        double my = y / win.getGuiScale();*/
        //? }
        if (WebHudOverlay.containsMouse(mx, my, client)) {
            browser.sendMouseMove((int) x, (int) y);
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void webgui$scrollToWebHudPseudoGui(long window, double horizontal, double vertical, CallbackInfo ci) {
        //? if fabric {
        MinecraftClient client = MinecraftClient.getInstance();
        //? } else {
        /*Minecraft client = Minecraft.getInstance();*/
        //? }
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        //? if fabric {
        if (win.getHandle() != window) {
        //? } else {
        /*//? if >=1.21.5 {
        if (win.handle() != window) {
        //? } else {
        if (win.getWindow() != window) {
        //? }*/
        //? }
            return;
        }
        //? if >=1.21.5 {
        //? if fabric {
        double mx = client.mouse.getScaledX(win);
        double my = client.mouse.getScaledY(win);
        //? } else {
        /*double mx = client.mouseHandler.getScaledXPos(win);
        double my = client.mouseHandler.getScaledYPos(win);*/
        //? }
        //? } else {
        //? if fabric {
        /*double mx = client.mouse.getX() / win.getScaleFactor();
        double my = client.mouse.getY() / win.getScaleFactor();*/
        //? } else {
        /*double mx = client.mouseHandler.xpos() / win.getGuiScale();
        double my = client.mouseHandler.ypos() / win.getGuiScale();*/
        //? }
        //? }
        if (!WebHudOverlay.containsMouse(mx, my, client)) {
            return;
        }
        MCEFBrowser browser = WebSession.browser();
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
