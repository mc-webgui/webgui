package land.webgui.mixin;

import com.cinemamod.mcef.MCEFBrowser;
import land.webgui.WebHudOverlay;
import land.webgui.WebSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void webgui$cancelVanillaForWebHudPseudoGui(long window, MouseInput input, int action, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        if (win.getHandle() != window) {
            return;
        }
        double mx = client.mouse.getScaledX(win);
        double my = client.mouse.getScaledY(win);
        if (WebHudOverlay.containsMouse(mx, my, client)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void webgui$scrollToWebHudPseudoGui(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!WebHudOverlay.shouldDeliverHudBrowserInput(client)) {
            return;
        }
        var win = client.getWindow();
        if (win.getHandle() != window) {
            return;
        }
        double mx = client.mouse.getScaledX(win);
        double my = client.mouse.getScaledY(win);
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
