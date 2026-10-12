package land.webgui.compat;

/**
 * Turns SDL text input on while a page has the keyboard.
 *
 * <p>From 26.3 SDL only delivers typed characters while text input is started, and the game
 * starts it for vanilla text fields only. Without this, key events arrive but characters do
 * not: shortcuts work, typing does nothing. No-op before 26.3.
 */
public final class TextInput {

    private TextInput() {
    }

    /** Starts delivering typed characters to {@code owner}. */
    public static void start(Object owner) {
        //? if >26.2 {
        /*net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null) {
            client.textInputManager().startTextInput(owner);
        }
        *///? }
    }

    /** Stops again, if {@code owner} is who asked. */
    public static void stop(Object owner) {
        //? if >26.2 {
        /*net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null) {
            client.textInputManager().stopTextInput(owner);
        }
        *///? }
    }
}
