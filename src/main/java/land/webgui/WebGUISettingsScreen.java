package land.webgui;

import land.webgui.compat.Mc;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod's own settings, reached from the game's mod list.
 *
 * Not a page in a browser: this is where a player goes when the browser is what is not
 * working.
 */
public class WebGUISettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 200;

    private final Screen parent;

    /** What the button steps through. A slider's constructor differs on every other
     *  Minecraft version; a button that cycles does not. */
    private static final int[] SCROLL_STEPS = {1, 3, 5, 10, 15, 20, 25, 30, 40};

    private Button devToolsButton;
    private Button scrollButton;
    public WebGUISettingsScreen(Screen parent) {
        super(Component.translatable("screen.webgui.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - BUTTON_WIDTH / 2;
        int top = this.height / 4 + 24;

        this.scrollButton = Button.builder(scrollLabel(), b -> {
            WebGUIClientConfig.setScrollNotches(nextScrollStep());
            this.scrollButton.setMessage(scrollLabel());
        }).bounds(left, top, BUTTON_WIDTH, 20).build();
        this.addRenderableWidget(this.scrollButton);

        this.devToolsButton = Button.builder(devToolsLabel(), b -> {
            WebGUIClientConfig.setDevTools(!WebGUIClientConfig.devTools());
            this.devToolsButton.setMessage(devToolsLabel());
        }).bounds(left, top + ROW_HEIGHT, BUTTON_WIDTH, 20).build();
        this.addRenderableWidget(this.devToolsButton);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(left, this.height - 32, BUTTON_WIDTH, 20).build());
    }

    private static int nextScrollStep() {
        int current = WebGUIClientConfig.scrollNotches();
        for (int step : SCROLL_STEPS) {
            if (step > current) {
                return step;
            }
        }
        return SCROLL_STEPS[0];
    }

    private Component scrollLabel() {
        return Component.translatable("screen.webgui.settings.scroll")
                .append(": ")
                .append(Component.literal(String.valueOf(WebGUIClientConfig.scrollNotches())));
    }

    private Component devToolsLabel() {
        return Component.translatable("screen.webgui.settings.devtools")
                .append(": ")
                .append(Component.translatable(WebGUIClientConfig.devTools() ? "options.on" : "options.off"));
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centre = this.width / 2;
        context.centeredText(this.font, this.title, centre, this.height / 4 - 8, 0xFFFFFFFF);
        int under = this.height / 4 + 24 + ROW_HEIGHT * 2;
        context.centeredText(this.font,
                Component.translatable("screen.webgui.settings.devtools.hint1"), centre, under, 0xFFA0A0A0);
        context.centeredText(this.font,
                Component.translatable("screen.webgui.settings.devtools.hint2"), centre, under + 12, 0xFFA0A0A0);
        // The real inspector is a launch argument, fixed before any mod runs; reporting
        // it is the most this screen can do.
        int inspector = under + 34;
        boolean on = WebGUIRemoteDebugging.enabled();
        context.centeredText(this.font,
                on ? Component.translatable("screen.webgui.settings.inspector.on", WebGUIRemoteDebugging.address())
                   : Component.translatable("screen.webgui.settings.inspector.off"),
                centre, inspector, on ? 0xFF80FF80 : 0xFFA0A0A0);
        if (!on) {
            context.centeredText(this.font,
                    Component.literal("--remote-debugging-port=" + WebGUIRemoteDebugging.SUGGESTED_PORT),
                    centre, inspector + 12, 0xFFFFFF80);
        }
    }
    *///? } else {
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centre = this.width / 2;
        context.drawCenteredString(this.font, this.title, centre, this.height / 4 - 8, 0xFFFFFFFF);
        int under = this.height / 4 + 24 + ROW_HEIGHT * 2;
        context.drawCenteredString(this.font,
                Component.translatable("screen.webgui.settings.devtools.hint1"), centre, under, 0xFFA0A0A0);
        context.drawCenteredString(this.font,
                Component.translatable("screen.webgui.settings.devtools.hint2"), centre, under + 12, 0xFFA0A0A0);
        // The real inspector is a launch argument, fixed before any mod runs; reporting
        // it is the most this screen can do.
        int inspector = under + 34;
        boolean on = WebGUIRemoteDebugging.enabled();
        context.drawCenteredString(this.font,
                on ? Component.translatable("screen.webgui.settings.inspector.on", WebGUIRemoteDebugging.address())
                   : Component.translatable("screen.webgui.settings.inspector.off"),
                centre, inspector, on ? 0xFF80FF80 : 0xFFA0A0A0);
        if (!on) {
            context.drawCenteredString(this.font,
                    Component.literal("--remote-debugging-port=" + WebGUIRemoteDebugging.SUGGESTED_PORT),
                    centre, inspector + 12, 0xFFFFFF80);
        }
    }
    //? }
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            Mc.openScreen(this.minecraft, this.parent);
        }
    }
}
