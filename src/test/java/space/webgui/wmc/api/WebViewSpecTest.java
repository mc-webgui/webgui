package space.webgui.wmc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebViewSpecTest {

    @Test
    void defaultsAreTransparentBecauseAViewIsDrawnOverTheGame() {
        assertTrue(WebViewSpec.of("app://x/").isTransparent());
    }

    @Test
    void settersDoNotMutateSoASpecCanBeATemplate() {
        WebViewSpec template = WebViewSpec.of("app://x/");
        WebViewSpec derived = template.size(800, 600);
        assertNotSame(template, derived);
        assertEquals(1280, template.width());
        assertEquals(800, derived.width());
    }

    @Test
    void refusesNonsenseRatherThanPassingItToChromium() {
        assertThrows(IllegalArgumentException.class, () -> WebViewSpec.of("app://x/").size(0, 100));
        assertThrows(IllegalArgumentException.class, () -> WebViewSpec.of("app://x/").frameRate(0));
        assertThrows(IllegalArgumentException.class, () -> WebViewSpec.of("app://x/").frameRate(1000));
    }
}
