package land.webgui.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic tests for {@link WebviewUrlBuilder#appendQueryParam} — the routine
 * used to attach the signed token (and other params) to a page URL. Touches no
 * Minecraft classes, so it runs as a plain JUnit unit test.
 */
class WebviewUrlBuilderTest {

    @Test
    void appendsFirstParamWithQuestionMark() {
        assertEquals(
                "https://example.com/?a=b",
                WebviewUrlBuilder.appendQueryParam("https://example.com/", "a", "b"));
    }

    @Test
    void appendsSecondParamWithAmpersand() {
        assertEquals(
                "https://example.com/?x=1&a=b",
                WebviewUrlBuilder.appendQueryParam("https://example.com/?x=1", "a", "b"));
    }

    @Test
    void insertsParamBeforeFragment() {
        assertEquals(
                "https://example.com/?a=b#section",
                WebviewUrlBuilder.appendQueryParam("https://example.com/#section", "a", "b"));
    }

    @Test
    void insertsParamBeforeFragmentWhenQueryAlreadyPresent() {
        assertEquals(
                "https://example.com/?x=1&a=b#/route",
                WebviewUrlBuilder.appendQueryParam("https://example.com/?x=1#/route", "a", "b"));
    }

    @Test
    void urlEncodesNameAndValue() {
        assertEquals(
                "https://example.com/?a+b=c%2Fd%26e",
                WebviewUrlBuilder.appendQueryParam("https://example.com/", "a b", "c/d&e"));
    }

    @Test
    void encodesTokenLikeValueWithDots() {
        // A real signed token is "<base64url>.<base64url>"; dots are URL-safe and preserved.
        String token = "MXwwMC00.aGFzaA";
        assertEquals(
                "https://hud.example/?webgui_token=" + token,
                WebviewUrlBuilder.appendQueryParam("https://hud.example/", "webgui_token", token));
    }

    @Test
    void nullUrlIsTreatedAsEmpty() {
        assertEquals("?a=b", WebviewUrlBuilder.appendQueryParam(null, "a", "b"));
    }
}
