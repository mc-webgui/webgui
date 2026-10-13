package land.webgui.mixin;

import land.webgui.browser.WebBrowser;
// Fabric only. NeoForge replaces the screen through ScreenEvent.Opening, so
// the whole body is compiled out there.
//? if fabric {
import land.webgui.WebGUIDeathScreen;
import land.webgui.WebViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps the vanilla death screen for the server's page, on Fabric.
 *
 * Injects into the screen setter rather than the death packet handler, which
 * has moved between versions.
 *
 * The setter moved too: Minecraft.setScreen up to 26.1.2, Gui.setScreen from
 * 26.2. Only the owning class is branched, on the same boundary as
 * {@link land.webgui.compat.Mc#openScreen}.
 *
 * Rewrites the argument instead of cancelling and calling setScreen again, to
 * avoid re-entering the method being injected into.
 *
 * NeoForge needs no mixin here: it has ScreenEvent.Opening.
 */
//? if >26.1.2 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///? } else {
@Mixin(Minecraft.class)
//? }
public class DeathScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen webgui$replaceDeathScreen(Screen screen) {
        if (!(screen instanceof DeathScreen) || !WebGUIDeathScreen.configured()) {
            return screen;
        }
        // No browser, no page. WebViewScreen closes itself when Chromium is not
        // ready, vanilla reopens the death screen because the player is still
        // dead, and we would replace it again — a loop that crashes the client.
        // Dying before Chromium finishes starting is exactly when that happens.
        if (!WebBrowser.ready()) {
            return screen;
        }
        WebGUIDeathScreen.setActive(true);
        return new WebViewScreen(WebGUIDeathScreen.url());
    }
}
//? }
