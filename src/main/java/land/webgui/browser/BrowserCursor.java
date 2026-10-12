package land.webgui.browser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
//? if >26.2 {
/*import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? } else {
import org.lwjgl.glfw.GLFW;
//? }

/**
 * The cursor a page asked for, on the game's window.
 *
 * <p>Up to 26.2 the cursor is set on the GLFW window and stays. From 26.3 the game applies
 * the last shape requested during its render pass and the arrow otherwise, so the shape is
 * only remembered here and {@link #request} asks for it each frame.
 *
 * <p>java-cef flattens Chromium's cursor to one of AWT's fourteen, so {@code grab} and
 * {@code zoom-in} arrive as a plain arrow. Reset on close, or the player keeps the page's
 * cursor in the world.
 */
final class BrowserCursor {

    // What the page last asked for, pushed at the graphics every frame.
    //? if >26.2 {
    /*private CursorType shape = CursorTypes.ARROW;
    *///? } else {
    /** GLFW's own, created once each and kept for as long as the game runs. */
    private static final Map<Integer, Long> SHAPES = new HashMap<>();
    //? }

    /** Said once per name, not once per pixel the mouse crosses. */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private String current = "default";

    /** Takes what the page asked for, if it is not what is already showing. */
    void set(String cssCursor) {
        String wanted = cssCursor == null ? "default" : cssCursor;
        if (wanted.equals(current)) {
            return;
        }
        current = wanted;
        // Once per distinct name. What a page writes in CSS and what arrives here are not the
        // same thing — java-cef turns Chromium's cursor into one of AWT's fourteen first — so
        // this is the only place the two can be compared.
        //? if >26.2 {
        /*shape = shapeFor(wanted);
        if (SEEN.add(wanted)) {
            land.webgui.WebGUIMod.LOGGER.info("webgui: cursor \"{}\" -> {}", wanted, shape);
        }
        *///? } else {
        long handle = shapeFor(wanted);
        if (SEEN.add(wanted)) {
            land.webgui.WebGUIMod.LOGGER.info("webgui: cursor \"{}\" -> {}",
                    wanted, handle == 0L ? "no shape, left as the arrow" : "a shape of its own");
        }
        apply(handle);
        //? }
    }

    /** Back to the game's own pointer. */
    void reset() {
        if ("default".equals(current)) {
            return;
        }
        current = "default";
        //? if >26.2 {
        /*shape = CursorTypes.ARROW;
        *///? } else {
        apply(0L);
        //? }
    }

    // Called from whatever draws the page; only requests made inside the render pass count.
    //? if >26.2 {
    /*void request(GuiGraphicsExtractor graphics) {
        graphics.requestCursor(shape);
    }

    // The shape the game will show, or its plain arrow.
    private static CursorType shapeFor(String cssCursor) {
        return switch (cssCursor) {
            case "pointer", "grab", "grabbing" -> CursorTypes.POINTING_HAND;
            case "text", "vertical-text" -> CursorTypes.IBEAM;
            case "crosshair", "cell" -> CursorTypes.CROSSHAIR;
            case "e-resize", "w-resize", "ew-resize", "col-resize" -> CursorTypes.RESIZE_EW;
            case "n-resize", "s-resize", "ns-resize", "row-resize" -> CursorTypes.RESIZE_NS;
            case "move", "all-scroll" -> CursorTypes.RESIZE_ALL;
            case "not-allowed", "no-drop" -> CursorTypes.NOT_ALLOWED;
            default -> CursorTypes.ARROW;
        };
    }
    *///? } else {
    private static void apply(long cursor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        //? if >=1.21.5 {
        long window = minecraft.getWindow().handle();
        //? } else {
        /*long window = minecraft.getWindow().getWindow();
        *///? }
        GLFW.glfwSetCursor(window, cursor);
    }

    /**
     * @return the GLFW cursor to show, or zero for the window's own
     */
    private static long shapeFor(String cssCursor) {
        int shape = switch (cssCursor) {
            case "pointer", "grab", "grabbing" -> GLFW.GLFW_HAND_CURSOR;
            case "text", "vertical-text" -> GLFW.GLFW_IBEAM_CURSOR;
            case "crosshair", "cell" -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case "e-resize", "w-resize", "ew-resize", "col-resize" -> GLFW.GLFW_HRESIZE_CURSOR;
            case "n-resize", "s-resize", "ns-resize", "row-resize" -> GLFW.GLFW_VRESIZE_CURSOR;
            //? if >=1.21.5 {
            // The diagonals and "move" are GLFW 3.4's, and only the newer Minecraft versions
            // ship an LWJGL that has them. AWT does tell these apart, so without this a page
            // resizing a box from its corner gets a plain arrow for no good reason.
            case "nw-resize", "se-resize", "nwse-resize" -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
            case "ne-resize", "sw-resize", "nesw-resize" -> GLFW.GLFW_RESIZE_NESW_CURSOR;
            case "move", "all-scroll" -> GLFW.GLFW_RESIZE_ALL_CURSOR;
            //? }
            default -> 0;
        };
        if (shape == 0) {
            return 0L;
        }
        // Created once per shape. Making one per hover would leak a handle for every pixel the
        // mouse crosses a link.
        return SHAPES.computeIfAbsent(shape, GLFW::glfwCreateStandardCursor);
    }
    //? }
}
