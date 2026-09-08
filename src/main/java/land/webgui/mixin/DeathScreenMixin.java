package land.webgui.mixin;

// Fabric only. NeoForge replaces the screen through ScreenEvent.Opening, and
// this class targets Yarn types that do not exist under Mojang mappings, so
// the whole body is compiled out there.
//? if fabric {
import de.keksuccino.rinku.Rinku;
import land.webgui.WebGUIDeathScreen;
import land.webgui.WebViewScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps the vanilla death screen for the server's page, on Fabric.
 *
 * Injecting into setScreen rather than the death packet handler is deliberate.
 * That handler is onDeathMessage on Yarn and handlePlayerCombatKill on Mojang
 * mappings and has moved between versions — exactly the shape of target that
 * has broken MouseMixin twice. {@code setScreen(Screen)} has one signature
 * across every Fabric target this mod builds for, so this mixin carries no
 * version conditionals, and it catches the screen whichever code path opened
 * it.
 *
 * Rewriting the argument rather than cancelling and calling setScreen again
 * keeps it out of a re-entrant call into the method it is injecting into.
 *
 * NeoForge needs no mixin here: it has ScreenEvent.Opening.
 */
@Mixin(MinecraftClient.class)
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
        if (!Rinku.isInitialized()) {
            return screen;
        }
        WebGUIDeathScreen.setActive(true);
        return new WebViewScreen(WebGUIDeathScreen.url());
    }
}
//? }
