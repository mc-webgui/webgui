package land.webgui;

import land.webgui.browser.WebBrowser;
import org.slf4j.Logger;
import space.webgui.wmc.api.ConsoleLevel;

/** Puts what a page writes to its console into the game's log. */
public final class WebviewBrowserConsoleLogger implements WebBrowser.Listener {

    private static final Logger LOG = WebGUIMod.LOGGER;

    @Override
    public void onConsole(WebBrowser browser, ConsoleLevel level, String message,
            String source, int line) {
        String text = formatMessage(message, source, line);
        if (level == null) {
            level = ConsoleLevel.INFO;
        }
        switch (level) {
            case ERROR -> LOG.error("[Web GUI] {}", text);
            case WARNING -> LOG.warn("[Web GUI] {}", text);
            case DEBUG -> LOG.debug("[Web GUI] {}", text);
            default -> LOG.info("[Web GUI] {}", text);
        }
    }

    private static String formatMessage(String message, String source, int line) {
        String msg = message != null ? message : "";
        if (source == null || source.isEmpty()) {
            return msg;
        }
        if (line > 0) {
            return source + ":" + line + " " + msg;
        }
        return source + " " + msg;
    }
}
