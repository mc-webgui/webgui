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
                  if (typeof window.webgui.respawn !== 'function') {
                    window.webgui.respawn = function () {
                      window.webgui.postToGame({ channel: 'respawn' });
                    };
                  }
                  if (typeof window.webgui.on !== 'function') {
                    window.webgui._hs = window.webgui._hs || {};
                    window.webgui.on = function (name, fn) {
                      var wrapped = function (e) { fn(e.detail); };
                      (window.webgui._hs[name] = window.webgui._hs[name] || []).push({ f: fn, w: wrapped });
                      window.addEventListener('webgui:' + name, wrapped);
                    };
                    window.webgui.off = function (name, fn) {
                      var arr = window.webgui._hs[name];
                      if (!arr) return;
                      for (var i = arr.length - 1; i >= 0; i--) {
                        if (arr[i].f === fn) {
                          window.removeEventListener('webgui:' + name, arr[i].w);
                          arr.splice(i, 1);
                          break;
                        }
                      }
                    };
                  }
                })();
                """;
    }
}
