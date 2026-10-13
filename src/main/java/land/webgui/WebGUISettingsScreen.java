package land.webgui;

import land.webgui.compat.Mc;

//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod's own settings, reached from the game's mod list.
 *
 * Not a page in a browser: this is where a player goes when the browser is what is not
 * working.
 *
 * Plain cycling buttons rather than sliders or a scrolling list: a slider's constructor
 * differs on every other Minecraft version and this screen has to build on ten of them.
 * What each option costs is said in its tooltip instead of on the screen, so the screen
 * stays the list of settings it is.
 */
public class WebGUISettingsScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 220;
    private static final int ROWS = 5;

    private static final int[] SCROLL_STEPS = {1, 3, 5, 10, 15, 20, 25, 30, 40};

    /** Zero last, and it means whatever Chromium would do unasked. */
    private static final int[] FRAME_RATE_STEPS = {30, 60, 90, 120, 144, 240, 0};

    private final Screen parent;

    private Button scrollButton;
    private Button frameRateButton;
    private Button downloadsButton;
    private Button autoplayButton;
    private Button devToolsButton;

    public WebGUISettingsScreen(Screen parent) {
        super(Component.translatable("screen.webgui.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - BUTTON_WIDTH / 2;
        int top = this.height / 2 - (ROWS * ROW_HEIGHT) / 2 - 16;

        this.scrollButton = row(left, top, scrollLabel(), "scroll", b -> {
            WebGUIClientConfig.setScrollNotches(next(SCROLL_STEPS, WebGUIClientConfig.scrollNotches()));
            this.scrollButton.setMessage(scrollLabel());
        });

        this.frameRateButton = row(left, top + ROW_HEIGHT, frameRateLabel(), "framerate", b -> {
            WebGUIClientConfig.setBrowserFrameRate(nextFrameRate());
            this.frameRateButton.setMessage(frameRateLabel());
        });

        this.downloadsButton = row(left, top + ROW_HEIGHT * 2, downloadsLabel(), "downloads", b -> {
            WebGUIClientConfig.setAskWhereToSaveDownloads(!WebGUIClientConfig.askWhereToSaveDownloads());
            this.downloadsButton.setMessage(downloadsLabel());
        });

        this.autoplayButton = row(left, top + ROW_HEIGHT * 3, autoplayLabel(), "autoplay", b -> {
            WebGUIClientConfig.setAllowPageAutoplay(!WebGUIClientConfig.allowPageAutoplay());
            this.autoplayButton.setMessage(autoplayLabel());
        });

        this.devToolsButton = row(left, top + ROW_HEIGHT * 4, devToolsLabel(), "devtools", b -> {
            WebGUIClientConfig.setDevTools(!WebGUIClientConfig.devTools());
            this.devToolsButton.setMessage(devToolsLabel());
        });

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(left, this.height - 32, BUTTON_WIDTH, 20).build());
    }

    private Button row(int left, int top, Component label, String key, Button.OnPress action) {
        Button button = Button.builder(label, action)
                .tooltip(Tooltip.create(Component.translatable("screen.webgui.settings." + key + ".tip")))
                .bounds(left, top, BUTTON_WIDTH, 20)
                .build();
        this.addRenderableWidget(button);
        return button;
    }

    private static int next(int[] steps, int current) {
        for (int step : steps) {
            if (step > current) {
                return step;
            }
        }
        return steps[0];
    }

    // Its own, because zero sorts first but belongs last: it is "no limit", not "slowest".
    private static int nextFrameRate() {
        int current = WebGUIClientConfig.browserFrameRate();
        for (int i = 0; i < FRAME_RATE_STEPS.length; i++) {
            if (FRAME_RATE_STEPS[i] == current) {
                return FRAME_RATE_STEPS[(i + 1) % FRAME_RATE_STEPS.length];
            }
        }
        return FRAME_RATE_STEPS[0];
    }

    private static Component value(String key, Component shown) {
        return Component.translatable("screen.webgui.settings." + key).append(": ").append(shown);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private Component scrollLabel() {
        return value("scroll", Component.literal(String.valueOf(WebGUIClientConfig.scrollNotches())));
    }

    private Component frameRateLabel() {
        int rate = WebGUIClientConfig.browserFrameRate();
        return value("framerate", rate <= 0
                ? Component.translatable("screen.webgui.settings.framerate.unlimited")
                : Component.translatable("screen.webgui.settings.framerate.value", rate));
    }

    private Component downloadsLabel() {
        return value("downloads", onOff(WebGUIClientConfig.askWhereToSaveDownloads()));
    }

    private Component autoplayLabel() {
        return value("autoplay", onOff(WebGUIClientConfig.allowPageAutoplay()));
    }

    private Component devToolsLabel() {
        return value("devtools", onOff(WebGUIClientConfig.devTools()));
    }

    // Only while the tools are on. The inspector is a developer's door, and a player who
    // never opens it should not be reading about launch arguments.
    private int inspectorTop() {
        return this.height / 2 - (ROWS * ROW_HEIGHT) / 2 - 16 + ROWS * ROW_HEIGHT + 6;
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centre = this.width / 2;
        context.centeredText(this.font, this.title, centre,
                this.height / 2 - (ROWS * ROW_HEIGHT) / 2 - 36, 0xFFFFFFFF);
        if (!WebGUIClientConfig.devTools()) {
            return;
        }
        boolean on = WebGUIRemoteDebugging.reachable();
        context.centeredText(this.font,
                on ? Component.translatable("screen.webgui.settings.inspector.on", WebGUIRemoteDebugging.address())
                   : Component.translatable("screen.webgui.settings.inspector.off"),
                centre, inspectorTop(), on ? 0xFF80FF80 : 0xFFA0A0A0);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centre = this.width / 2;
        context.drawCenteredString(this.font, this.title, centre,
                this.height / 2 - (ROWS * ROW_HEIGHT) / 2 - 36, 0xFFFFFFFF);
        if (!WebGUIClientConfig.devTools()) {
            return;
        }
        boolean on = WebGUIRemoteDebugging.reachable();
        context.drawCenteredString(this.font,
                on ? Component.translatable("screen.webgui.settings.inspector.on", WebGUIRemoteDebugging.address())
                   : Component.translatable("screen.webgui.settings.inspector.off"),
                centre, inspectorTop(), on ? 0xFF80FF80 : 0xFFA0A0A0);
    }
    //? }

    @Override
    public void onClose() {
        Mc.openScreen(this.minecraft, this.parent);
    }
}
