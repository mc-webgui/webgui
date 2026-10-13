package land.webgui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a page URL is allowed to be.
 *
 * A URL reaches this from a server, so the set of schemes it may name is a security boundary
 * rather than a convenience: {@code file://} would read the player's disk and
 * {@code javascript:} would run in whatever page is open.
 */
class WebGUIAssetServerSchemeTest {

    private static final String REFUSED = "about:blank";

    @Test
    void remoteSchemesPassThroughUntouched() {
        for (String url : new String[] {
                "https://example.com/menu",
                "http://example.com/menu?a=1",
                "wss://example.com/socket",
                "ws://example.com/socket" }) {
            assertEquals(url, WebGUIAssetServer.resolve(url), url + " is a page hosted the ordinary way");
        }
    }

    @Test
    void schemeIsReadWithoutRegardForCase() {
        assertEquals("HTTPS://Example.com", WebGUIAssetServer.resolve("HTTPS://Example.com"));
        assertNotEquals(REFUSED, WebGUIAssetServer.resolve("WebGUI://index.html"));
    }

    @Test
    void ourOwnSchemeIsAccepted() {
        // Where it resolves to depends on whether the page server is up, which this test does
        // not start. That it is not refused is the part that belongs here.
        assertNotEquals(REFUSED, WebGUIAssetServer.resolve("webgui://index.html"));
    }

    @Test
    void refusesSchemesThatAreNotPages() {
        for (String url : new String[] {
                "file:///etc/passwd",
                "file://C:/Windows/win.ini",
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "ftp://example.com/x",
                "chrome://settings",
                "example.com/no-scheme-at-all" }) {
            assertEquals(REFUSED, WebGUIAssetServer.resolve(url), url + " must not be opened");
        }
    }

    @Test
    void refusesTheFormsThatUsedToBeAccepted() {
        // webgui:x and webgui:/x were taken as the same thing as webgui://x. One spelling is
        // easier to check than three, and these now say so rather than working by accident.
        assertEquals(REFUSED, WebGUIAssetServer.resolve("webgui:index.html"));
        assertEquals(REFUSED, WebGUIAssetServer.resolve("webgui:/index.html"));
    }

    @Test
    void nothingIsLeftAloneRatherThanRefused() {
        assertNull(WebGUIAssetServer.resolve(null));
        assertEquals("   ", WebGUIAssetServer.resolve("   "));
    }
}
