package land.webgui;

import land.webgui.browser.WebBrowser;
//? if <=26.2 {
import org.lwjgl.glfw.GLFW;
//? }

/**
 * Sizing/scale helpers for the embedded browser. The browser view is the physical framebuffer size
 * (so its texture draws 1:1 → crisp on HiDPI), and CEF page zoom scales the content up by the OS
 * content scale so the page's CSS viewport stays logical and independent of Minecraft's GUI Scale.
 * Uses only the public {@link WebBrowser#setZoomLevel} API — no device_scale_factor, no reflection.
 */
final class WebViewLayout {
    private WebViewLayout() {}

    /** OS content scale of the current window (e.g. 2 on a Retina display), or 1 if unknown. */
    static float contentScale() {
        //? if >26.2 {
        /*// The game answers this itself from 26.3, where there is no GLFW left to ask.
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return 1f;
        }
        float density = client.getWindow().getPixelDensity();
        return density > 0f ? density : 1f;
        *///? } else {
        long handle = GLFW.glfwGetCurrentContext();
        if (handle == 0L) {
            return 1f;
        }
        float[] sx = new float[1];
        float[] sy = new float[1];
        GLFW.glfwGetWindowContentScale(handle, sx, sy);
        return sx[0] > 0f ? sx[0] : 1f;
        //? }
    }

    /** Browser view size = physical framebuffer pixels, so the OSR texture is drawn 1:1 (crisp). */
    static int[] browserSize(int framebufferWidth, int framebufferHeight) {
        return new int[] {Math.max(1, framebufferWidth), Math.max(1, framebufferHeight)};
    }

    /**
     * CEF zoom level that scales content by the content scale: {@code zoomFactor = 1.2^level}, so
     * {@code level = log(scale) / log(1.2)}. Result: CSS viewport = framebuffer / scale (logical),
     * rendered at framebuffer resolution. 0 when no scaling is needed.
     */
    static double zoomLevel() {
        float scale = contentScale();
        return scale > 1f ? Math.log(scale) / Math.log(1.2) : 0.0;
    }

    /** Applies the content-scale zoom. CEF may reset zoom on navigation, so call after load too. */
    static void applyZoom(WebBrowser browser) {
        if (browser == null) {
            return;
        }
        try {
            browser.setZoomLevel(zoomLevel());
        } catch (Throwable t) {
            WebGUIMod.LOGGER.warn("[WebGUI] setZoomLevel failed: {}", t.toString());
        }
    }
}
