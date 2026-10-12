package land.webgui.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The handful of things Minecraft renamed, in one place.
 *
 * <p>Every one of these is a line that would otherwise be written twice at every call site —
 * once for the old name and once for the new — and the cost of that is not the typing. It is
 * that a new Minecraft version means finding all of them, and the ones that are missed do not
 * fail to compile: they take the wrong branch and misbehave quietly. That happened in this mod
 * already, with predicates written as {@code >=26.2-neoforge}: correct until a Fabric target
 * on 26.2 existed, then silently false.
 *
 * <p>So the rule here is: if the game renamed it and this mod calls it more than once, it
 * belongs in this class. Porting to the next version then means reading one file.
 *
 * <p>What does <em>not</em> belong here is anything a loader differs on — that is a different
 * axis and lives in the loader branches — and anything with a return type that changes, since
 * hiding the difference is the one thing a method cannot do. Identifiers are the example:
 * {@link Ids} exists for them and has to be branched at its own signature.
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
