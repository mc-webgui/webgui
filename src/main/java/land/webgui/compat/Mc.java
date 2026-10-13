package land.webgui.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The handful of things Minecraft renamed, in one place.
 *
 * <p>If the game renamed it and this mod calls it more than once, it belongs here: a missed
 * call site does not fail to compile, it takes the wrong branch quietly.
 *
 * <p>Loader differences do not belong here — those live in the loader branches — nor does
 * anything whose return type changes, which a method cannot hide. See {@link Ids}.
 */
public final class Mc {

    private Mc() {
    }

    /**
     * The screen the player is looking at, or null for none.
     *
     * <p>Moved onto the gui in 26.2: {@code minecraft.screen} became
     * {@code minecraft.gui.screen()}.
     */
    public static Screen screen(Minecraft client) {
        if (client == null) {
            return null;
        }
        //? if >26.1.2 {
        /*return client.gui.screen();
        *///? } else {
        return client.screen;
        //? }
    }

    /** Whether a screen of any kind is open. */
    public static boolean hasScreen(Minecraft client) {
        return screen(client) != null;
    }

    /** Opens a screen, or closes the current one when given null. */
    public static void openScreen(Minecraft client, Screen screen) {
        if (client == null) {
            return;
        }
        //? if >26.1.2 {
        /*client.gui.setScreen(screen);
        *///? } else {
        client.setScreen(screen);
        //? }
    }

    /**
     * Says something to the player, in their chat.
     *
     * <p>{@code displayClientMessage} became {@code sendSystemMessage} in 26, and the overlay
     * flag went with it — nothing here ever passed true for it.
     */
    public static void tell(Minecraft client, Component message) {
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.player == null) {
                return;
            }
            //? if >=26 {
            /*client.player.sendSystemMessage(message);
            *///? } else {
            client.player.displayClientMessage(message, false);
            //? }
        });
    }

    /** The same, for a plain string, with the mod's own prefix on it. */
    public static void tell(String message) {
        tell(Minecraft.getInstance(), Component.literal("[WebGUI] " + message));
    }
}
