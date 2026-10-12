# Test pages

Dev-only. Never shipped: the released jar carries no pages of its own, and a server
operator's `config/webgui/web` is theirs.

Copy them into the target you are testing:

    cp dev/testpages/* versions/<target>/run/config/webgui/web/

Then, in game:

    /webgui gui @s webgui:index.html
    /webgui hud @s webgui:hud.html

What each page is for:

| page | checks |
|---|---|
| `index.html` | bridge present, `isHud`, `assetsBase`, and `webgui://` links navigating |
| `bridge.html` | page → game channels, live client data, bound entity, server events, death, respawn |
| `input.html` | typing, selection by double click, native dropdown, cursor shapes, scroll speed |
| `download.html` | files the page makes (including an awkward name), and one from the web |
| `hud.html` | the overlay: see-through, glanceable, interactive on `` ` `` |
| `death.html` | the vanilla death screen replaced, the death payload, respawn, Escape refused |

The death page is the one that needs more than copying: the server only suppresses the
vanilla screen when it has been asked to, so set it in `config/webgui/server.json` and
reload. No reconnect — `reload` pushes the url to everyone already online.

    "deathScreenUrl": "webgui:/death.html"

    /webgui reload
