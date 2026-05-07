package land.webgui;

import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.slf4j.Logger;

public final class WebviewBrowserConsoleLogger extends CefDisplayHandlerAdapter {

    private static final Logger LOG = WebGUIMod.LOGGER;

    @Override
    public boolean onConsoleMessage(
            CefBrowser browser,
            CefSettings.LogSeverity level,
            String message,
            String source,
            int line) {
        String text = formatMessage(message, source, line);
        if (level == null) {
            level = CefSettings.LogSeverity.LOGSEVERITY_DEFAULT;
        }
        switch (level) {
            case LOGSEVERITY_ERROR:
            case LOGSEVERITY_FATAL:
                LOG.error("[Web GUI] {}", text);
                break;
            case LOGSEVERITY_WARNING:
                LOG.warn("[Web GUI] {}", text);
                break;
            case LOGSEVERITY_VERBOSE:
                LOG.debug("[Web GUI] {}", text);
                break;
            case LOGSEVERITY_DISABLE:
                break;
            case LOGSEVERITY_DEFAULT:
            case LOGSEVERITY_INFO:
            default:
                LOG.info("[Web GUI] {}", text);
                break;
        }
        return false;
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
