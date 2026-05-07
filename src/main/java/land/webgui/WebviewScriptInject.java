package land.webgui;

public final class WebviewScriptInject {
    private WebviewScriptInject() {}

    public static String bridgeSetup() {
        return """
                (function () {
                  if (typeof window.webgui === 'undefined') window.webgui = {};
                  if (typeof window.webgui.postToGame !== 'function') {
                    window.webgui.postToGame = function (payload) {
                      var msg = typeof payload === 'string' ? payload : JSON.stringify(payload);
                      if (typeof window.cefQuery !== 'function') {
                        console.warn('[webgui] cefQuery unavailable');
                        return;
                      }
                      window.cefQuery({
                        request: msg,
                        persistent: false,
                        onSuccess: function () {},
                        onFailure: function (code, err) { console.error('[webgui]', code, err); }
                      });
                    };
                  }
                  if (typeof window.webgui.closeGui !== 'function') {
                    window.webgui.closeGui = function () {
                      window.webgui.postToGame({ channel: 'close' });
                    };
                  }
                })();
                """;
    }
}
