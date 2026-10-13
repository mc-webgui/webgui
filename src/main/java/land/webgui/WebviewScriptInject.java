package land.webgui;

public final class WebviewScriptInject {
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private WebviewScriptInject() {}

    /**
     * The bridge a page talks to the game through.
     *
     * Every definition is guarded, and nothing here touches the DOM, so this can run
     * before the document's own scripts and again after them without doing any harm —
     * which it has to, because an inline script is parsed and run long before a page
     * finishes loading.
     *
     * @param assetsBase where the server's own pages are served from, or empty when the
     *                   server ships none
     */
    public static String bridgeSetup(String assetsBase, boolean hudMode) {
        // Encoded rather than interpolated: it is a URL built at runtime, and a quote in
        // it would turn this whole script into a syntax error at the worst moment.
        String base = GSON.toJson(assetsBase == null ? "" : assetsBase);
        return """
                (function () {
                  if (typeof window.webgui === 'undefined') window.webgui = {};
                  // Where this server's own files live, so a page can build a URL to one
                  // without knowing which port the local page server ended up on.
                  window.webgui.assetsBase = %s;
                  // Whether this page is the HUD overlay rather than a full screen. A HUD
                  // is sized for glanceable content, so a page worth writing for both
                  // needs to know which one it is in.
                  window.webgui.isHud = %s;""".formatted(base, hudMode) + """

                  // webgui://whatever.html, as a link a page can actually click.
                  //
                  // The scheme is this mod's shorthand for "a file this server ships"; the
                  // browser knows nothing about it, so written in an href it would simply
                  // fail to navigate. Resolved here to the address the local page server
                  // ended up on, which a page cannot know by itself — the port is chosen at
                  // runtime and the path carries a per-session token.
                  if (typeof window.webgui.resolve !== 'function') {
                    window.webgui.resolve = function (url) {
                      if (typeof url !== 'string') return url;
                      var trimmed = url.trim();
                      if (!/^webgui:[/][/]/i.test(trimmed)) return trimmed;
                      var base = window.webgui.assetsBase || '';
                      if (!base) return trimmed;
                      var rest = trimmed.slice(9);
                      if (rest === '' || rest.charAt(0) === '?') rest = 'index.html' + rest;
                      while (base.charAt(base.length - 1) === '/') base = base.slice(0, -1);
                      return base + '/' + rest;
                    };
                  }
                  // Once, however often this script is injected: it runs before the document's
                  // own scripts and again after them.
                  if (!window.webgui._links) {
                    window.webgui._links = true;
                    document.addEventListener('click', function (ev) {
                      if (ev.defaultPrevented || ev.button !== 0 || ev.metaKey || ev.ctrlKey) return;
                      var node = ev.target;
                      while (node && node.nodeType === 1 && node.tagName !== 'A') node = node.parentNode;
                      if (!node || node.nodeType !== 1) return;
                      var href = node.getAttribute('href');
                      if (!href || !/^webgui:[/][/]/i.test(href)) return;
                      var resolved = window.webgui.resolve(href);
                      if (resolved === href) return;
                      ev.preventDefault();
                      window.location.href = resolved;
                    }, true);
                  }
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
